package com.georgeci.moneysurfer.feature.goal

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.feature.goal.contribution.GoalContributionScreen
import com.georgeci.moneysurfer.feature.goal.details.GoalDetailsScreen
import com.georgeci.moneysurfer.feature.goal.edit.GoalEditScreen
import com.georgeci.moneysurfer.feature.goal.list.GoalsScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.SurferPaneSceneStrategy

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val goalNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Goals>(
        metadata = SurferPaneSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) {
        GoalsScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToGoalCreation = { navigator.push(Route.GoalCreation) },
            onNavigateToGoalDetails = { goalId -> navigator.push(Route.GoalDetails(goalId.value)) },
        )
    }

    entry<Route.GoalDetails>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        GoalDetailsScreen(
            goalId = GoalId(key.goalId),
            onNavigateBack = { navigator.pop() },
            onNavigateToEdit = { goalId -> navigator.push(Route.GoalEdit(goalId.value)) },
            onNavigateToContribution = { goalId, mode ->
                navigator.push(Route.GoalContribution(goalId = goalId.value, mode = mode.name))
            },
        )
    }

    entry<Route.GoalCreation>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        GoalEditScreen(
            goalId = null,
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.GoalEdit>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        GoalEditScreen(
            goalId = GoalId(key.goalId),
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.GoalContribution>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        GoalContributionScreen(
            goalId = GoalId(key.goalId),
            mode = GoalContributionMode.fromName(key.mode),
            onNavigateBack = { navigator.pop() },
        )
    }
}
