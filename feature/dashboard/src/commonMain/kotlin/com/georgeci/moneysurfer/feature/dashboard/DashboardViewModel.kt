package com.georgeci.moneysurfer.feature.dashboard

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.ConvertedTotal
import com.georgeci.moneysurfer.domain.model.ExchangeRateSnapshot
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Instant

@KoinViewModel
class DashboardViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val getRecentTransactions: GetRecentTransactionsUseCase,
    private val getGoals: GetGoalsUseCase,
    private val getExchangeRates: GetExchangeRatesUseCase,
    private val convertAccountsTotal: ConvertAccountsTotalUseCase,
    uiPreferences: UiPreferences,
    hostCapabilities: HostCapabilities,
) : MviViewModel<DashboardState, DashboardEvent, DashboardEffect>(
    initialState = DashboardState.Loading,
) {

    private val isOffline: Boolean = hostCapabilities.isOffline

    /**
     * Widget order and visibility. Normalized again here so the screen renders a sound layout
     * whatever supplies the pref — the DataStore-backed binding already normalizes on decode, but
     * an in-memory or future remote one need not, and `normalized()` is idempotent.
     */
    private val layout = uiPreferences.dashboardLayout.flow
        .map { it.normalized() }
        .onStart { emit(DashboardLayoutConfig.DEFAULT) }

    init {
        observeDashboard()
    }

    override fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.OnAccountClick -> postSideEffect(
                DashboardEffect.NavigateToAccountDetails(event.accountId),
            )
            is DashboardEvent.OnTransactionClick -> postSideEffect(
                DashboardEffect.NavigateToTransactionDetails(event.transactionId),
            )
            DashboardEvent.OnAddAccountClick -> postSideEffect(DashboardEffect.NavigateToAccountCreation)
            DashboardEvent.OnSeeAllTransactionsClick ->
                postSideEffect(DashboardEffect.NavigateToTransactionsList)
            DashboardEvent.OnAddTransactionClick -> postSideEffect(
                DashboardEffect.NavigateToTransactionCreation(accountId = null),
            )
            is DashboardEvent.OnAddTransactionForAccountClick ->
                postSideEffect(DashboardEffect.NavigateToTransactionCreation(accountId = event.accountId))
            DashboardEvent.OnManageAccountsClick -> postSideEffect(DashboardEffect.NavigateToAccountsManage)
            DashboardEvent.OnSettingsClick -> postSideEffect(DashboardEffect.NavigateToSettings)
            DashboardEvent.OnCustomizeClick -> postSideEffect(DashboardEffect.NavigateToCustomize)
            DashboardEvent.OnSeeAllGoalsClick -> postSideEffect(DashboardEffect.NavigateToGoals)
            is DashboardEvent.OnGoalClick -> postSideEffect(DashboardEffect.NavigateToGoalDetails(event.goalId))
        }
    }

    private fun observeDashboard() {
        launch {
            combine(
                getAccounts().onStart { emit(emptyList()) },
                getRecentTransactions(),
                getGoals(),
                getExchangeRates().onStart { emit(null) },
                layout,
            ) { accounts, transactions, goals, rates, layoutConfig ->
                val balance = accounts.convertedTotal(rates)?.toBalanceUi()
                DashboardState.Content(
                    accounts = accounts.map { it.toUi() },
                    transactions = transactions
                        .filter { it.type != TransactionType.OPENING_BALANCE }
                        .take(RECENT_TRANSACTIONS_LIMIT)
                        .map { it.toUi() },
                    formattedTotalBalance = balance?.headline,
                    otherCurrencyTotals = balance?.notConverted.orEmpty(),
                    ratesAsOf = balance?.asOf,
                    workspaceName = null,
                    workspaceInitial = null,
                    greeting = null,
                    formattedTrendDelta = null,
                    goals = goals.take(DASHBOARD_GOALS_LIMIT).map { it.toUi() },
                    isOffline = isOffline,
                    layout = layoutConfig,
                )
            }.collect { newContent -> updateState { newContent } }
        }
    }

    /**
     * The converted total. `null` until a base currency is known — a workspace is always selected
     * by the time accounts exist, so this only covers the first frame after launch and the
     * signed-out state, both of which render the empty balance anyway.
     */
    private fun List<Account>.convertedTotal(rates: ExchangeRateSnapshot?): ConvertedTotal? =
        rates?.let { convertAccountsTotal(this, it.baseCurrency, it.rates) }

    /**
     * Splits the total into what the balance widget renders.
     *
     * When nothing could be priced in the base currency — a workspace whose accounts are all in
     * currencies the cache does not cover — the headline falls back to the largest remaining
     * bucket instead of going empty. `formattedTotalBalance == null` is the screen's "no accounts
     * at all" signal, and letting an unconvertible balance trip it would tell a user who *has*
     * money to add their first account.
     */
    private fun ConvertedTotal.toBalanceUi(): BalanceUi {
        val leftOver = unconverted.map { MoneyFormatter.format(it.amount, it.currencyCode) }
        return when (val converted = total) {
            null -> BalanceUi(
                headline = leftOver.firstOrNull(),
                notConverted = leftOver.drop(1),
                asOf = null,
            )
            else -> BalanceUi(
                headline = MoneyFormatter.format(converted, baseCurrency),
                notConverted = leftOver,
                asOf = asOf?.asIsoDate(),
            )
        }
    }

    /** ISO date — the "as of" label has to be unambiguous, and the app ships no date locale rules. */
    private fun Instant.asIsoDate(): String =
        toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private fun Account.toUi() = AccountUi(
        id = id,
        name = name,
        formattedBalance = MoneyFormatter.format(balance, currencyCode),
        currency = currencyCode.value,
    )

    private fun SavingsGoalSummary.toUi() = GoalUi(
        id = goal.id,
        name = goal.title,
        formattedSaved = MoneyFormatter.format(progress.saved, goal.currencyCode),
        formattedTarget = MoneyFormatter.format(progress.target, goal.currencyCode),
        progress = progress.percent.toFloat(),
    )

    private fun Transaction.toUi() = TransactionUi(
        id = id,
        title = note.ifBlank { "No description" },
        formattedAmount = MoneyFormatter.format(money.abs(), currencyCode),
        isExpense = type == TransactionType.EXPENSE,
        categoryHueSeed = categoryId?.value.orEmpty(),
    )
}

@optics
sealed interface DashboardState {
    data object Loading : DashboardState

    @optics
    data class Content(
        val accounts: List<AccountUi>,
        val transactions: List<TransactionUi>,
        /**
         * Every account balance converted into the workspace base currency, or null when no
         * account could be priced there (no accounts at all, or none in a currency the cached
         * rates cover).
         */
        val formattedTotalBalance: String?,
        /**
         * Per-currency totals for the accounts no cached rate could convert. Shown as a note
         * beside the headline — dropping them was the old bug, and they are never folded into
         * the headline at a rate the app does not have.
         */
        val otherCurrencyTotals: List<String> = emptyList(),
        /**
         * ISO date the converting rates were published, or null when the total needed no rates.
         * Renders as the staleness signal so a total computed offline says how old it is.
         */
        val ratesAsOf: String? = null,
        val workspaceName: String?,
        val workspaceInitial: String?,
        val greeting: String?,
        val formattedTrendDelta: String?,
        val goals: List<GoalUi> = emptyList(),
        val isOffline: Boolean = false,
        /** Which widgets the screen renders, in order. */
        val layout: DashboardLayoutConfig = DashboardLayoutConfig.DEFAULT,
    ) : DashboardState {

        /**
         * Single source of truth for the "no transactions logged yet" decision so screens
         * never re-derive it — keeps the empty-state vs. content choice off the composable.
         */
        val recentTransactionsEmpty: Boolean
            get() = transactions.isEmpty()

        companion object
    }

    companion object
}

/** The three strings the balance widget needs, already formatted. */
private data class BalanceUi(
    val headline: String?,
    val notConverted: List<String>,
    val asOf: String?,
)

data class AccountUi(
    val id: AccountId,
    val name: String,
    val formattedBalance: String,
    val currency: String,
)

data class GoalUi(
    val id: GoalId,
    val name: String,
    val formattedSaved: String,
    val formattedTarget: String,
    val progress: Float,
)

data class TransactionUi(
    val id: TransactionId,
    val title: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
)

sealed interface DashboardEvent {
    data class OnAccountClick(val accountId: AccountId) : DashboardEvent
    data class OnTransactionClick(val transactionId: TransactionId) : DashboardEvent
    data object OnAddAccountClick : DashboardEvent
    data object OnAddTransactionClick : DashboardEvent
    data object OnSeeAllTransactionsClick : DashboardEvent
    data class OnAddTransactionForAccountClick(val accountId: AccountId) : DashboardEvent
    data object OnManageAccountsClick : DashboardEvent
    data object OnSettingsClick : DashboardEvent
    data object OnCustomizeClick : DashboardEvent
    data object OnSeeAllGoalsClick : DashboardEvent
    data class OnGoalClick(val goalId: GoalId) : DashboardEvent
}

sealed interface DashboardEffect {
    data class NavigateToAccountDetails(val accountId: AccountId) : DashboardEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : DashboardEffect
    data object NavigateToAccountCreation : DashboardEffect
    data object NavigateToAccountsManage : DashboardEffect
    data class NavigateToTransactionCreation(val accountId: AccountId?) : DashboardEffect
    data object NavigateToSettings : DashboardEffect
    data object NavigateToCustomize : DashboardEffect
    data object NavigateToTransactionsList : DashboardEffect
    data object NavigateToGoals : DashboardEffect
    data class NavigateToGoalDetails(val goalId: GoalId) : DashboardEffect
}

private const val RECENT_TRANSACTIONS_LIMIT = 5

/** The widget shows two rows at most; reading more of the list would be wasted work. */
private const val DASHBOARD_GOALS_LIMIT = 2
