package com.georgeci.moneysurfer.feature.transaction.list

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.isoWeek
import com.georgeci.moneysurfer.domain.util.periodWindow
import com.georgeci.moneysurfer.domain.util.shiftPeriod
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@KoinViewModel
class TransactionsByAccountViewModel(
    accountId: AccountId?,
    private val getTransactionsByAccount: GetTransactionsByAccountUseCase,
    private val getAccountById: GetAccountByIdUseCase,
    private val uiPreferences: UiPreferences,
    private val clock: ClockUseCase,
) : MviViewModel<TransactionsByAccountState, TransactionsByAccountEvent, TransactionsByAccountEffect>(
    initialState = TransactionsByAccountState.Loading(accountId = accountId),
) {

    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate get() = clock.now().toLocalDateTime(zone).date

    private val typeFilter = MutableStateFlow(TransactionTypeFilter.All)

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
            is TransactionsByAccountEvent.OnTypeFilterChanged ->
                typeFilter.value = event.filter
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        launch {
            val accountId = currentState.accountId
            val account = if (accountId != null) getAccountById(accountId) else null

            combine(
                uiPreferences.transactionsPeriodMode.flow.distinctUntilChanged(),
                anchorDate,
            ) { mode, anchor -> Period(mode = mode, anchor = anchor) }
                // A new window invalidates the accumulated paging: the first page of the new
                // period is what the user is about to look at, not page 7 of the old one.
                .onEach { pageLimit.value = PAGE_SIZE }
                .flatMapLatest { period ->
                    val page = pageLimit.flatMapLatest { limit ->
                        // One row over the page: its presence is what "there is more" means, and
                        // it costs one row rather than a second COUNT query.
                        getTransactionsByAccount.window(accountId, period.window, limit + 1)
                            .map { rows -> Page(rows = rows, limit = limit) }
                    }
                    combine(
                        page,
                        getTransactionsByAccount.totals(accountId, period.window),
                        typeFilter,
                    ) { loaded, totals, filter ->
                        buildContent(
                            accountId = accountId,
                            account = account,
                            period = period,
                            page = loaded,
                            totals = totals,
                            filter = filter,
                        )
                    }
                }
                .collect { content -> updateState { content } }
        }
    }

    private fun buildContent(
        accountId: AccountId?,
        account: Account?,
        period: Period,
        page: Page,
        totals: List<TransactionTotal>,
        filter: TransactionTypeFilter,
    ): TransactionsByAccountState.Content {
        val canLoadMore = page.rows.size > page.limit
        val visible = page.rows.take(page.limit)
        val currency = visible.firstOrNull()?.currencyCode
            ?: account?.currencyCode
            ?: CurrencyCode("USD")

        val filtered = when (filter) {
            TransactionTypeFilter.All -> visible
            TransactionTypeFilter.Expenses -> visible.filter { it.type == TransactionType.EXPENSE }
            TransactionTypeFilter.Income -> visible.filter { it.type == TransactionType.INCOME }
        }
        // Already ordered by the query's (operationDate, operationAt, createdAt) DESC — grouping
        // preserves encounter order, so no re-sort is needed here.
        val groups = filtered.groupBy { it.transaction.operationDate }
            .map { (date, txns) ->
                val net = txns.fold(Money.zero()) { acc, t -> acc + t.signedMoney() }
                val groupCurrency = txns.first().currencyCode
                TransactionGroupUi(
                    date = date,
                    dateLabel = dateLabel(date),
                    netFormatted = MoneyFormatter.format(net, groupCurrency),
                    netPositive = !net.isNegative(),
                    transactions = txns.map { t ->
                        val transaction = t.transaction
                        val categoryName = t.categoryName
                        TransactionRowUi(
                            id = transaction.id,
                            title = transaction.note.ifBlank { categoryName.orEmpty() },
                            subtitle = categoryName.orEmpty(),
                            formattedAmount = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode),
                            isExpense = transaction.type == TransactionType.EXPENSE,
                            categoryHueSeed = categoryName ?: transaction.categoryId?.value.orEmpty(),
                        )
                    },
                )
            }

        return TransactionsByAccountState.Content(
            accountId = accountId,
            accountName = account?.name.orEmpty(),
            groups = groups,
            summary = buildSummary(totals, currency),
            typeFilter = filter,
            isFiltered = filter != TransactionTypeFilter.All,
            periodMode = period.mode,
            period = periodLabel(period),
            // Paging forward past the current period would only ever show an empty list, so the
            // pager stops there; going back is always allowed.
            canGoToPreviousPeriod = period.mode != TransactionPeriodMode.AllTime,
            canGoToNextPeriod = period.mode != TransactionPeriodMode.AllTime && today !in period.window,
            canLoadMore = canLoadMore,
        )
    }

    /**
     * Summary of the whole period, not of the loaded page: [totals] come straight from the
     * database aggregation, so paging cannot change these numbers.
     *
     * Rows in other currencies are dropped rather than added to the displayed one — mixing minor
     * units across currencies would produce a confidently wrong number.
     */
    private fun buildSummary(
        totals: List<TransactionTotal>,
        currency: CurrencyCode,
    ): TransactionSummaryUi {
        val inCurrency = totals.filter { it.currencyCode == currency }
        val income = inCurrency.sumOfMoney { it.type == TransactionType.INCOME }
        val expense = inCurrency.sumOfMoney { it.type == TransactionType.EXPENSE }
        val net = income - expense
        return TransactionSummaryUi(
            incomeFormatted = "+" + MoneyFormatter.format(income, currency),
            expenseFormatted = "−" + MoneyFormatter.format(expense, currency),
            netFormatted = (if (net.isNegative()) "−" else "+") +
                MoneyFormatter.format(net.abs(), currency),
            netPositive = !net.isNegative(),
        )
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

    private fun List<TransactionTotal>.sumOfMoney(predicate: (TransactionTotal) -> Boolean): Money =
        filter(predicate).fold(Money.zero()) { acc, total -> acc + total.total }

    private val CategorizedTransaction.currencyCode: CurrencyCode get() = transaction.currencyCode
    private val CategorizedTransaction.type: TransactionType get() = transaction.type

    private fun CategorizedTransaction.signedMoney(): Money = when (transaction.type) {
        TransactionType.EXPENSE -> -transaction.money.abs()
        else -> transaction.money.abs()
    }

    /** One fetch: at most `limit + 1` rows, the extra one only signalling that more exist. */
    private data class Page(val rows: List<CategorizedTransaction>, val limit: Int)

    /** The visible period: what the user picked, where they paged to, and the resulting bounds. */
    private data class Period(
        val mode: TransactionPeriodMode,
        val anchor: LocalDate,
        val window: TransactionPeriodWindow = periodWindow(mode, anchor),
    )
}

@optics
sealed interface TransactionsByAccountState {
    val accountId: AccountId?

    @optics
    data class Loading(override val accountId: AccountId?) : TransactionsByAccountState {
        companion object
    }

    @optics
    data class Content(
        override val accountId: AccountId?,
        val accountName: String,
        val groups: List<TransactionGroupUi>,
        val summary: TransactionSummaryUi,
        val typeFilter: TransactionTypeFilter,
        val isFiltered: Boolean,
        val periodMode: TransactionPeriodMode,
        val period: TransactionPeriodUi,
        val canGoToPreviousPeriod: Boolean,
        val canGoToNextPeriod: Boolean,
        val canLoadMore: Boolean,
    ) : TransactionsByAccountState {
        val isEmpty: Boolean get() = groups.isEmpty()

        companion object
    }

    companion object
}

data class TransactionGroupUi(
    val date: LocalDate,
    val dateLabel: TransactionDateUi,
    val netFormatted: String,
    val netPositive: Boolean,
    val transactions: List<TransactionRowUi>,
)

/** Day-header label, resolved to text by the screen. */
sealed interface TransactionDateUi {
    data object Today : TransactionDateUi
    data object Yesterday : TransactionDateUi
    data class Exact(val date: LocalDate) : TransactionDateUi
}

/** Pager label, resolved to text by the screen — see the `PeriodPager` design component. */
sealed interface TransactionPeriodUi {
    data class Month(val monthNumber: Int, val year: Int) : TransactionPeriodUi
    data class Week(
        val from: LocalDate,
        val to: LocalDate,
        val weekNumber: Int,
        val weekYear: Int,
    ) : TransactionPeriodUi

    data object AllTime : TransactionPeriodUi
}

data class TransactionRowUi(
    val id: TransactionId,
    val title: String,
    val subtitle: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
)

data class TransactionSummaryUi(
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val netPositive: Boolean,
)

enum class TransactionTypeFilter { All, Expenses, Income }

sealed interface TransactionsByAccountEvent {
    data object OnBackClick : TransactionsByAccountEvent
    data object OnAddTransactionClick : TransactionsByAccountEvent
    data class OnTransactionClick(val transactionId: TransactionId) : TransactionsByAccountEvent
    data class OnTypeFilterChanged(val filter: TransactionTypeFilter) : TransactionsByAccountEvent
    data object OnPreviousPeriodClick : TransactionsByAccountEvent
    data object OnNextPeriodClick : TransactionsByAccountEvent
    data class OnPeriodModeChanged(val mode: TransactionPeriodMode) : TransactionsByAccountEvent
    data object OnLoadMore : TransactionsByAccountEvent
}

sealed interface TransactionsByAccountEffect {
    data object NavigateBack : TransactionsByAccountEffect
    data class NavigateToTransactionCreation(val accountId: AccountId?) : TransactionsByAccountEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : TransactionsByAccountEffect
}
