package com.georgeci.moneysurfer.feature.dashboard

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.formattedTotalsByCurrency
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DashboardViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val getRecentTransactions: GetRecentTransactionsUseCase,
    private val getGoals: GetGoalsUseCase,
    uiPreferences: UiPreferences,
    offlineBuildFlags: OfflineBuildFlags,
) : MviViewModel<DashboardState, DashboardEvent, DashboardEffect>(
    initialState = DashboardState.Loading,
) {

    private val isOffline: Boolean = offlineBuildFlags.isOffline

    /**
     * Widget order and visibility. Normalized on read so a layout persisted by an older build
     * still shows widgets shipped since — the codec cannot invent them, only this can.
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
                layout,
            ) { accounts, transactions, goals, layoutConfig ->
                val totals = accounts.formattedTotalsByCurrency()
                DashboardState.Content(
                    accounts = accounts.map { it.toUi() },
                    transactions = transactions
                        .filter { it.type != TransactionType.OPENING_BALANCE }
                        .take(RECENT_TRANSACTIONS_LIMIT)
                        .map { it.toUi() },
                    formattedTotalBalance = totals.firstOrNull(),
                    otherCurrencyTotals = totals.drop(1),
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
        /** Total for the most-used currency, or null when there are no accounts. */
        val formattedTotalBalance: String?,
        /**
         * Totals for the remaining currencies. Shown as a note beside the headline: adding them
         * into it would need FX rates the app does not have, and dropping them was the old bug.
         */
        val otherCurrencyTotals: List<String> = emptyList(),
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
    data object NavigateToTransactionsList : DashboardEffect
    data object NavigateToGoals : DashboardEffect
    data class NavigateToGoalDetails(val goalId: GoalId) : DashboardEffect
}

private const val RECENT_TRANSACTIONS_LIMIT = 5

/** The widget shows two rows at most; reading more of the list would be wasted work. */
private const val DASHBOARD_GOALS_LIMIT = 2
