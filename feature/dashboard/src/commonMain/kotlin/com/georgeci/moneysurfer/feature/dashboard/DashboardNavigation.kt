package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.primitives.AccountId
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
)

/**
 * The screen's whole side-effect half: one branch per [DashboardEffect], nothing else. It lives
 * beside [DashboardNavigation] rather than inside `DashboardScreen` because a flat dispatch table
 * that grows with the destination list is what pushes the entry point over the complexity limit,
 * and there is nothing here to simplify — every branch is a distinct destination.
 */
@Composable
internal fun HandleDashboardEffects(
    viewModel: DashboardViewModel,
    navigation: DashboardNavigation,
) {
    viewModel.HandleSideEffect { effect ->
        when (effect) {
            is DashboardEffect.NavigateToAccountDetails ->
                navigation.onNavigateToAccountDetails(effect.accountId)

            is DashboardEffect.NavigateToTransactionDetails ->
                navigation.onNavigateToTransactionDetails(effect.transactionId)

            DashboardEffect.NavigateToAccountCreation -> navigation.onNavigateToAccountCreation()
            DashboardEffect.NavigateToAccountsManage -> navigation.onNavigateToAccountsManage()
            is DashboardEffect.NavigateToTransactionCreation ->
                navigation.onNavigateToTransactionCreation(effect.accountId)

            DashboardEffect.NavigateToTransferCreation -> navigation.onNavigateToTransferCreation()
            DashboardEffect.NavigateToSettings -> navigation.onNavigateToSettings()
            DashboardEffect.NavigateToCustomize -> navigation.onNavigateToCustomize()
            DashboardEffect.NavigateToTransactionsList -> navigation.onNavigateToTransactionsList()
            DashboardEffect.NavigateToGoals -> navigation.onNavigateToGoals()
            is DashboardEffect.NavigateToGoalDetails -> navigation.onNavigateToGoalDetails(effect.goalId)
            DashboardEffect.NavigateToBudgetCreation -> navigation.onNavigateToBudgetCreation()
        }
    }
}
