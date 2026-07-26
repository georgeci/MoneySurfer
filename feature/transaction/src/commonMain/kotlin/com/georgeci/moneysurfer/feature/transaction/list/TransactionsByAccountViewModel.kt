package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.isoWeek
import com.georgeci.moneysurfer.domain.util.shiftPeriod
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilterStore
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.compile
import com.georgeci.moneysurfer.feature.transaction.filter.resolveWindow
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

/** Rows fetched per page. One screenful is ~15 rows, so a page covers a long scroll burst. */
private const val PAGE_SIZE = 200

/**
 * Enough rendered rows that the list is comfortably scrollable, so the normal scroll-driven
 * load-more can take over. Below this, a filtered page auto-advances — see
 * [shouldLoadMoreToFillFilteredList].
 */
private const val AUTO_LOAD_UNTIL_SCROLLABLE = 20

/**
 * Whether a filtered page is too sparse to scroll and should pull its next raw page.
 *
 * A filter that matches only a handful of the loaded rows leaves too few to scroll, so without
 * this the user could never reach matches deeper in the window and an all-miss first page would
 * wrongly read as "nothing matches". Bounded by the window: a month stops at the month's rows.
 *
 * The real cure is to filter in SQL (the DAO already has an FTS `searchByText`); this keeps the
 * in-memory page honest until that lands.
 */
private fun shouldLoadMoreToFillFilteredList(content: TransactionsByAccountState.Content): Boolean =
    content.isFiltered && content.canLoadMore && content.renderedRowCount < AUTO_LOAD_UNTIL_SCROLLABLE

@KoinViewModel
class TransactionsByAccountViewModel(
    accountId: AccountId?,
    private val getTransactionsByAccount: GetTransactionsByAccountUseCase,
    private val getAccountById: GetAccountByIdUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val filterStore: TransactionFilterStore,
    private val uiPreferences: UiPreferences,
    private val clock: ClockUseCase,
) : MviViewModel<TransactionsByAccountState, TransactionsByAccountEvent, TransactionsByAccountEffect>(
    initialState = TransactionsByAccountState.Loading(accountId = accountId),
) {

    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate get() = clock.now().toLocalDateTime(zone).date

    /**
     * A date inside the visible period, not the period itself — the window is derived from it and
     * the current mode, so switching month → week keeps the user roughly where they were.
     *
     * Starts at today and is intentionally *not* persisted: reopening the list on a period the
     * user last browsed weeks ago would look like missing data.
     */
    private val anchorDate = MutableStateFlow(today)

    /** Grows by [PAGE_SIZE] on each load-more; reset whenever the window changes. */
    private val pageLimit = MutableStateFlow(PAGE_SIZE)

    init {
        observeData()
    }

    override fun onEvent(event: TransactionsByAccountEvent) {
        when (event) {
            TransactionsByAccountEvent.OnBackClick ->
                postSideEffect(TransactionsByAccountEffect.NavigateBack)
            TransactionsByAccountEvent.OnAddTransactionClick ->
                postSideEffect(TransactionsByAccountEffect.NavigateToTransactionCreation(currentState.accountId))
            is TransactionsByAccountEvent.OnTransactionClick ->
                postSideEffect(TransactionsByAccountEffect.NavigateToTransactionDetails(event.transactionId))
            is TransactionsByAccountEvent.OnSearchQueryChanged -> filterStore.setQuery(event.query)
            TransactionsByAccountEvent.OnClearFiltersClick -> {
                // The search text goes too, unlike `clear()` on its own: this is the empty state's
                // CTA, and it promises the list back — leaving the query applied would answer the
                // tap with the same empty screen.
                filterStore.clear()
                filterStore.setQuery("")
            }
            TransactionsByAccountEvent.OnOpenFiltersClick ->
                // Carry the anchor the list is paged to: the filter screen's live result count
                // resolves the same window from it, so `Apply · N results` matches the list the
                // user returns to rather than always counting today's period.
                postSideEffect(
                    TransactionsByAccountEffect.NavigateToFilters(
                        accountId = currentState.accountId,
                        anchorEpochDay = anchorDate.value.toEpochDays(),
                    ),
                )
            TransactionsByAccountEvent.OnPreviousPeriodClick -> shiftAnchor(by = -1)
            TransactionsByAccountEvent.OnNextPeriodClick -> shiftAnchor(by = 1)
            is TransactionsByAccountEvent.OnPeriodModeChanged -> launch {
                uiPreferences.transactionsPeriodMode.set(event.mode)
            }
            TransactionsByAccountEvent.OnLoadMore -> loadMore()
        }
    }

    private fun shiftAnchor(by: Int) {
        val mode = (currentState as? TransactionsByAccountState.Content)?.periodMode ?: return
        anchorDate.value = shiftPeriod(mode, anchorDate.value, by)
    }

    private fun loadMore() {
        val content = currentState as? TransactionsByAccountState.Content ?: return
        if (!content.canLoadMore) return
        pageLimit.value += PAGE_SIZE
    }

    /**
     * Two layers, on purpose. The *window* — pager mode, anchor and the filters' date range — is
     * what the database is queried with, and a change to it invalidates paging. Every other filter
     * narrows the rows already loaded, so typing in the search field must not throw the user back
     * to page one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        launch {
            val accountId = currentState.accountId
            val account = if (accountId != null) getAccountById(accountId) else null

            val loaded = combine(
                uiPreferences.transactionsPeriodMode.flow.distinctUntilChanged(),
                anchorDate,
                filterStore.filters.map { it.dateRange }.distinctUntilChanged(),
            ) { mode, anchor, dateRange ->
                Period(
                    mode = mode,
                    anchor = anchor,
                    dateRange = dateRange,
                    window = resolveWindow(dateRange, mode, anchor, today),
                )
            }
                .distinctUntilChanged()
                // A new window invalidates the accumulated paging: the first page of the new
                // period is what the user is about to look at, not page 7 of the old one.
                .onEach { pageLimit.value = PAGE_SIZE }
                .flatMapLatest { period ->
                    combine(
                        pageFlow(accountId, period),
                        getTransactionsByAccount.totals(accountId, period.window),
                    ) { page, totals -> Loaded(period = period, page = page, totals = totals) }
                }

            combine(
                loaded,
                filterStore.filters,
                getAccounts(),
                getCategories(),
            ) { rows, filters, accounts, categories ->
                buildContent(
                    accountId = accountId,
                    account = account,
                    loaded = rows,
                    filters = filters,
                    accounts = accounts,
                    categories = categories,
                )
            }.collect { content ->
                updateState { content }
                // Filtering runs in memory over the loaded page, but the scroll-driven load-more
                // can only fire once the rendered list is long enough to scroll. While a filter is
                // active and too few rows match to scroll, keep pulling the next raw page until the
                // list is scrollable again (normal paging takes over) or the window is exhausted.
                if (shouldLoadMoreToFillFilteredList(content)) pageLimit.value += PAGE_SIZE
            }
        }
    }

    private fun pageFlow(accountId: AccountId?, period: Period): Flow<Page> =
        pageLimit.flatMapLatest { limit ->
            // One row over the page: its presence is what "there is more" means, and it costs one
            // row rather than a second COUNT query.
            getTransactionsByAccount.window(accountId, period.window, limit + 1)
                .map { rows -> Page(rows = rows, limit = limit) }
        }

    private fun buildContent(
        accountId: AccountId?,
        account: Account?,
        loaded: Loaded,
        filters: TransactionFilters,
        accounts: List<Account>,
        categories: List<Category>,
    ): TransactionsByAccountState.Content {
        val page = loaded.page
        val canLoadMore = page.rows.size > page.limit
        val visible = page.rows.take(page.limit)
        val currency = summaryCurrency(account, loaded.totals)
        // An account-scoped list is already restricted by the query; intersecting it with a
        // different account picked on the filter screen would silently show nothing.
        val effective = if (accountId != null) filters.copy(accountIds = emptySet()) else filters

        return TransactionsByAccountState.Content(
            accountId = accountId,
            accountName = account?.name.orEmpty(),
            groups = groupByDate(
                rows = matchedRows(visible, effective),
                accountNames = accounts.associate { it.id to it.name },
                dateLabel = ::dateLabel,
            ),
            // A row only has to name its account where the list mixes several. Scoped to one, the
            // toolbar already says which, and repeating it on every row would be noise.
            showAccountOnRows = accountId == null,
            summary = buildSummary(loaded.totals, currency),
            query = filters.query,
            filters = chips(effective, accounts, categories),
            activeFilterCount = effective.activeCount,
            isFiltered = effective.isNarrowed,
            periodMode = loaded.period.mode,
            period = periodLabel(loaded.period),
            // The pager is the date window's source only while no explicit range is set — see
            // `resolveWindow`. Hiding it there is what keeps the two from competing.
            showPeriodPager = effective.dateRange == TransactionDateRange.FollowPeriod,
            // Paging forward past the current period would only ever show an empty list, so the
            // pager stops there; going back is always allowed.
            canGoToPreviousPeriod = loaded.period.mode != TransactionPeriodMode.AllTime,
            canGoToNextPeriod = loaded.period.mode != TransactionPeriodMode.AllTime &&
                today !in loaded.period.window,
            canLoadMore = canLoadMore,
        )
    }

    private fun matchedRows(
        visible: List<CategorizedTransaction>,
        filters: TransactionFilters,
    ): List<CategorizedTransaction> {
        // Compile once, then test each row: the amount fields are parsed a single time for the
        // whole page rather than re-parsed per row.
        val matcher = filters.compile()
        val matched = visible.filter { matcher.matches(it) }
        // Already ordered by the query's (operationDate, operationAt, createdAt) DESC — reversing
        // the flat list is what "oldest first" means, inside each day as well as between days.
        return if (filters.sort == TransactionSort.Oldest) matched.reversed() else matched
    }

    private fun periodLabel(period: Period): TransactionPeriodUi = when (period.mode) {
        TransactionPeriodMode.Month -> TransactionPeriodUi.Month(
            monthNumber = period.anchor.month.number,
            year = period.anchor.year,
        )
        TransactionPeriodMode.Week -> {
            val week = period.anchor.isoWeek()
            TransactionPeriodUi.Week(
                from = period.window.from ?: period.anchor,
                to = period.window.to ?: period.anchor,
                weekNumber = week.weekNumber,
                weekYear = week.weekYear,
            )
        }
        TransactionPeriodMode.AllTime -> TransactionPeriodUi.AllTime
    }

    /**
     * Semantic, not textual: the wording of "Today" and of an absolute date is the screen's
     * business, so the ViewModel stays free of user-facing strings and of locale assumptions.
     */
    private fun dateLabel(date: LocalDate): TransactionDateUi {
        val today = today
        return when (date) {
            today -> TransactionDateUi.Today
            LocalDate.fromEpochDays(today.toEpochDays() - 1) -> TransactionDateUi.Yesterday
            else -> TransactionDateUi.Exact(date)
        }
    }

    /** One fetch: at most `limit + 1` rows, the extra one only signalling that more exist. */
    private data class Page(val rows: List<CategorizedTransaction>, val limit: Int)

    /** Everything one window produced: the page of rows and the period totals behind it. */
    private data class Loaded(
        val period: Period,
        val page: Page,
        val totals: List<TransactionTotal>,
    )

    /** The visible period: what the user picked, where they paged to, and the resulting bounds. */
    private data class Period(
        val mode: TransactionPeriodMode,
        val anchor: LocalDate,
        val dateRange: TransactionDateRange,
        val window: TransactionPeriodWindow,
    )
}
