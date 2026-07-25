package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.feature.dashboard.customize.DashboardCustomizeScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route

val dashboardNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Dashboard> {
        DashboardScreen(
            onNavigateToAccountCreation = { navigator.push(Route.AccountCreation()) },
            onNavigateToAccountsManage = { navigator.push(Route.AccountsManage) },
            onNavigateToTransactionCreation = { accountId ->
                navigator.push(Route.TransactionCreation(accountId = accountId?.value))
            },
            onNavigateToAccountDetails = { accountId ->
                navigator.push(Route.AccountDetails(accountId.value))
            },
            onNavigateToTransactionDetails = { transactionId ->
                navigator.push(Route.TransactionDetails(transactionId.value))
            },
            onNavigateToSettings = { navigator.push(Route.Settings) },
            onNavigateToCustomize = { navigator.push(Route.DashboardCustomize) },
            onNavigateToTransactionsList = { navigator.push(Route.TransactionsByAccount()) },
            onNavigateToGoals = { navigator.push(Route.Goals) },
            onNavigateToGoalDetails = { goalId -> navigator.push(Route.GoalDetails(goalId.value)) },
        )
    }

    entry<Route.DashboardCustomize> {
        DashboardCustomizeScreen(
            onNavigateBack = { navigator.pop() },
        )
    }
}
