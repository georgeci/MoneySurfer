package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.utils.HandleSideEffect

/**
 * Where the dashboard can send the user. Grouped rather than declared one lambda per destination:
 * the screen reaches a dozen places, and `kotlin:S107` allows seven parameters. Same remedy and
 * same shape as `WorkspaceSelectorNavigation` — see AGENTS.md → UI Rules.
 */
data class DashboardNavigation(
    val onNavigateToAccountCreation: () -> Unit,
    val onNavigateToAccountsManage: () -> Unit,
    val onNavigateToTransactionCreation: (accountId: AccountId?) -> Unit,
    val onNavigateToTransferCreation: () -> Unit,
    val onNavigateToAccountDetails: (AccountId) -> Unit,
    val onNavigateToTransactionDetails: (TransactionId) -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onNavigateToCustomize: () -> Unit,
    val onNavigateToTransactionsList: () -> Unit,
    val onNavigateToGoals: () -> Unit,
    val onNavigateToGoalDetails: (GoalId) -> Unit,
    val onNavigateToBudgetCreation: () -> Unit,
    val onNavigateToBudgets: () -> Unit,
    val onNavigateToBudgetDetails: (BudgetId) -> Unit,
)

/**
 * The screen's whole side-effect half: one branch per [DashboardEffect], nothing else.
 *
 * A plain function rather than a lambda inside the composable, for two reasons. A flat dispatch
 * table that grows with the destination list is what pushed `DashboardScreen` over the complexity
 * limit; and a mis-wired branch here sends the user to the wrong screen, which is worth a test —
 * one that needs no composition, no lifecycle and no view model to run.
 */
// One straight-line branch per destination — flat, exhaustive, and checked by the compiler. Its
// "complexity" is the number of places the dashboard can reach, not tangled logic; grouping the
// branches behind nested types to please the metric would cost exactly the readability the flat
// table buys. Same call as `SettingsScreen`.
@Suppress("CyclomaticComplexMethod")
internal fun DashboardNavigation.navigate(effect: DashboardEffect) {
    when (effect) {
        is DashboardEffect.NavigateToAccountDetails -> onNavigateToAccountDetails(effect.accountId)
        is DashboardEffect.NavigateToTransactionDetails -> onNavigateToTransactionDetails(effect.transactionId)
        DashboardEffect.NavigateToAccountCreation -> onNavigateToAccountCreation()
        DashboardEffect.NavigateToAccountsManage -> onNavigateToAccountsManage()
        is DashboardEffect.NavigateToTransactionCreation -> onNavigateToTransactionCreation(effect.accountId)
        DashboardEffect.NavigateToTransferCreation -> onNavigateToTransferCreation()
        DashboardEffect.NavigateToSettings -> onNavigateToSettings()
        DashboardEffect.NavigateToCustomize -> onNavigateToCustomize()
        DashboardEffect.NavigateToTransactionsList -> onNavigateToTransactionsList()
        DashboardEffect.NavigateToGoals -> onNavigateToGoals()
        is DashboardEffect.NavigateToGoalDetails -> onNavigateToGoalDetails(effect.goalId)
        DashboardEffect.NavigateToBudgetCreation -> onNavigateToBudgetCreation()
        DashboardEffect.NavigateToBudgets -> onNavigateToBudgets()
        is DashboardEffect.NavigateToBudgetDetails -> onNavigateToBudgetDetails(effect.budgetId)
    }
}

@Composable
internal fun HandleDashboardEffects(
    viewModel: DashboardViewModel,
    navigation: DashboardNavigation,
) {
    viewModel.HandleSideEffect(navigation::navigate)
}
