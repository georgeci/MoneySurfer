package com.georgeci.moneysurfer.feature.transaction.filters

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilterStore
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.feature.transaction.filter.compile
import com.georgeci.moneysurfer.feature.transaction.filter.resolveWindow
import com.georgeci.moneysurfer.feature.transaction.list.collapseSplitLegs
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

/**
 * How many rows the live result count looks at.
 *
 * The count is a real query, not an estimate — the same windowed query the list itself runs, with
 * the same matcher applied — because an estimate that disagreed with the list the user lands on
 * would be worse than no number at all. It is capped so that "Apply" on an unbounded date range
 * cannot pull an entire history into memory on every keystroke; beyond the cap the screen says
 * "500+" rather than a wrong exact number.
 */
private const val COUNT_LIMIT = 500

/**
 * The filter screen's draft.
 *
 * Nothing here touches [TransactionFilterStore] until `Apply`: the draft is local, so backing out
 * with `Cancel` — or with the system back gesture — leaves the list exactly as it was.
 */
@KoinViewModel
class TransactionFiltersViewModel(
    private val accountId: AccountId?,
    /**
     * The day the list is paged to, or null to fall back to today. Only used while the range still
     * follows the period pager — a preset or custom range resolves its own window regardless.
     */
    private val anchorEpochDay: Long?,
    private val filterStore: TransactionFilterStore,
    private val getTransactionsByAccount: GetTransactionsByAccountUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val uiPreferences: UiPreferences,
    private val clock: ClockUseCase,
) : MviViewModel<TransactionFiltersState, TransactionFiltersEvent, TransactionFiltersEffect>(
    initialState = TransactionFiltersState(
        draft = TransactionFilters.Empty,
        // The accounts section is meaningless on a list already scoped to one account: the query
        // is restricted to it, so picking another one could only ever produce an empty list.
        showAccounts = accountId == null,
    ),
) {

    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate get() = clock.now().toLocalDateTime(zone).date

    private val draft = MutableStateFlow(filterStore.filters.value)

    init {
        observeData()
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onEvent(event: TransactionFiltersEvent) {
        when (event) {
            TransactionFiltersEvent.OnCancelClick ->
                postSideEffect(TransactionFiltersEffect.NavigateBack)
            TransactionFiltersEvent.OnResetClick -> draft.value = draft.value.cleared()
            TransactionFiltersEvent.OnApplyClick -> {
                filterStore.commit(draft.value)
                postSideEffect(TransactionFiltersEffect.NavigateBack)
            }
            is TransactionFiltersEvent.OnDatePresetClick -> edit { withPreset(event.preset) }
            TransactionFiltersEvent.OnCustomDateClick -> edit { withCustomRange() }
            is TransactionFiltersEvent.OnCustomFromPicked -> edit { withCustomFrom(event.date) }
            is TransactionFiltersEvent.OnCustomToPicked -> edit { withCustomTo(event.date) }
            is TransactionFiltersEvent.OnTypeSelected -> edit { copy(type = event.type) }
            is TransactionFiltersEvent.OnAccountToggled -> edit {
                copy(accountIds = accountIds.toggle(event.accountId))
            }
            is TransactionFiltersEvent.OnCategoryToggled -> edit {
                copy(categoryIds = categoryIds.toggle(event.categoryId))
            }
            TransactionFiltersEvent.OnAllCategoriesClick -> edit { copy(categoryIds = emptySet()) }
            is TransactionFiltersEvent.OnMinAmountChanged -> edit { copy(minAmount = event.text) }
            is TransactionFiltersEvent.OnMaxAmountChanged -> edit { copy(maxAmount = event.text) }
            is TransactionFiltersEvent.OnRecurringOnlyChanged -> edit { copy(recurringOnly = event.enabled) }
            is TransactionFiltersEvent.OnPlannedOnlyChanged -> edit { copy(plannedOnly = event.enabled) }
            is TransactionFiltersEvent.OnSortSelected -> edit { copy(sort = event.sort) }
        }
    }

    private fun edit(block: TransactionFilters.() -> TransactionFilters) {
        draft.value = draft.value.block()
    }

    /** Tapping a selected preset clears it — otherwise a date range could only ever be swapped. */
    private fun TransactionFilters.withPreset(preset: TransactionDatePreset): TransactionFilters {
        val already = (dateRange as? TransactionDateRange.Preset)?.preset == preset
        return copy(
            dateRange = if (already) {
                TransactionDateRange.FollowPeriod
            } else {
                TransactionDateRange.Preset(preset)
            },
        )
    }

    private fun TransactionFilters.withCustomRange(): TransactionFilters =
        if (dateRange is TransactionDateRange.Custom) {
            copy(dateRange = TransactionDateRange.FollowPeriod)
        } else {
            copy(dateRange = TransactionDateRange.Custom(from = null, to = null))
        }

    private fun TransactionFilters.withCustomFrom(date: LocalDate?): TransactionFilters =
        copy(dateRange = customRange().copy(from = date))

    private fun TransactionFilters.withCustomTo(date: LocalDate?): TransactionFilters =
        copy(dateRange = customRange().copy(to = date))

    /** Picking a From/To date is itself the switch to a custom range. */
    private fun TransactionFilters.customRange(): TransactionDateRange.Custom =
        dateRange as? TransactionDateRange.Custom ?: TransactionDateRange.Custom(null, null)

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        launch {
            val periodMode = uiPreferences.transactionsPeriodMode.flow.first()
            // The window the list is actually showing when the range still follows the pager: the
            // day it was paged to, not necessarily today.
            val anchor = anchorEpochDay?.let(LocalDate::fromEpochDays) ?: today
            val counted = draft
                .map { filters -> filters.forCount() }
                .distinctUntilChanged()
                .flatMapLatest { filters ->
                    val window = resolveWindow(filters.dateRange, periodMode, anchor, today)
                    val matcher = filters.compile()
                    getTransactionsByAccount.window(accountId, window, COUNT_LIMIT + 1)
                        // Matched *then* collapsed, the same two steps in the same order the list
                        // applies: the button promises how many rows the user is about to see, and
                        // a receipt split across three categories is one of them, not three.
                        .map { rows -> collapseSplitLegs(rows.filter { matcher.matches(it) }).size }
                }

            combine(draft, counted, getAccounts(), getCategories()) { filters, count, accounts, categories ->
                TransactionFiltersState(
                    draft = filters,
                    showAccounts = accountId == null,
                    accounts = accounts.filterNot { it.archived },
                    categories = categories,
                    resultCount = count.coerceAtMost(COUNT_LIMIT),
                    resultCountCapped = count > COUNT_LIMIT,
                )
            }.collect { state -> updateState { state } }
        }
    }

    /**
     * The draft as the count query sees it: an account-scoped list ignores the account selection,
     * exactly as the list does.
     */
    private fun TransactionFilters.forCount(): TransactionFilters =
        if (accountId != null) copy(accountIds = emptySet()) else this
}

/**
 * A plain data class rather than the sealed `Loading` / `Content` the repo uses elsewhere: the
 * screen has no async-load phase. The draft is available synchronously from
 * [TransactionFilterStore] at construction, so there is never a "Loading" state to model — the
 * accounts, categories and result count merely refine an already-usable screen as they arrive.
 */
data class TransactionFiltersState(
    val draft: TransactionFilters,
    val showAccounts: Boolean,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val resultCount: Int = 0,
    /** The count hit [COUNT_LIMIT]; the screen shows "500+" rather than an exact number. */
    val resultCountCapped: Boolean = false,
) {
    val canReset: Boolean get() = draft.activeCount > 0
}

sealed interface TransactionFiltersEvent {
    data object OnCancelClick : TransactionFiltersEvent
    data object OnResetClick : TransactionFiltersEvent
    data object OnApplyClick : TransactionFiltersEvent
    data class OnDatePresetClick(val preset: TransactionDatePreset) : TransactionFiltersEvent
    data object OnCustomDateClick : TransactionFiltersEvent
    data class OnCustomFromPicked(val date: LocalDate?) : TransactionFiltersEvent
    data class OnCustomToPicked(val date: LocalDate?) : TransactionFiltersEvent
    data class OnTypeSelected(val type: TransactionTypeFilter) : TransactionFiltersEvent
    data class OnAccountToggled(val accountId: AccountId) : TransactionFiltersEvent
    data class OnCategoryToggled(val categoryId: CategoryId) : TransactionFiltersEvent
    data object OnAllCategoriesClick : TransactionFiltersEvent
    data class OnMinAmountChanged(val text: String) : TransactionFiltersEvent
    data class OnMaxAmountChanged(val text: String) : TransactionFiltersEvent
    data class OnRecurringOnlyChanged(val enabled: Boolean) : TransactionFiltersEvent
    data class OnPlannedOnlyChanged(val enabled: Boolean) : TransactionFiltersEvent
    data class OnSortSelected(val sort: TransactionSort) : TransactionFiltersEvent
}

sealed interface TransactionFiltersEffect {
    data object NavigateBack : TransactionFiltersEffect
}
