package com.georgeci.moneysurfer.feature.budget

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.feature.budget.details.BudgetDetailsScreen
import com.georgeci.moneysurfer.feature.budget.edit.BudgetEditScreen
import com.georgeci.moneysurfer.feature.budget.list.BudgetsScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.SurferPaneSceneStrategy

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val budgetNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Budgets>(
        metadata = SurferPaneSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) {
        BudgetsScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToBudgetCreation = { navigator.push(Route.BudgetCreation()) },
            onNavigateToBudgetDetails = { budgetId ->
                navigator.push(Route.BudgetDetails(budgetId = budgetId.value))
            },
        )
    }

    entry<Route.BudgetDetails>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        BudgetDetailsScreen(
            budgetId = BudgetId(key.budgetId),
            onNavigateBack = { navigator.pop() },
            onNavigateToBudgetEdit = { budgetId ->
                navigator.push(Route.BudgetCreation(budgetId = budgetId.value))
            },
            onNavigateToTransactionDetails = { transactionId ->
                navigator.push(Route.TransactionDetails(transactionId.value))
            },
        )
    }

    entry<Route.BudgetCreation>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        BudgetEditScreen(
            budgetId = key.budgetId?.let(::BudgetId),
            onNavigateBack = { navigator.pop() },
        )
    }
}
