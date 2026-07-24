package com.georgeci.moneysurfer.feature.goal.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.feature.goal.formatGoalDate
import com.georgeci.moneysurfer.feature.goal.labelRes
import com.georgeci.moneysurfer.feature.goal.toUi
import com.georgeci.moneysurfer.uikit.components.SurferEmptyState
import com.georgeci.moneysurfer.uikit.components.base.SurferAddFab
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AsyncState
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.LocalDate
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goals_add
import moneysurfer.feature.goal.generated.resources.goals_card_deadline
import moneysurfer.feature.goal.generated.resources.goals_empty_subtitle
import moneysurfer.feature.goal.generated.resources.goals_empty_title
import moneysurfer.feature.goal.generated.resources.goals_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GoalsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGoalCreation: () -> Unit,
    onNavigateToGoalDetails: (GoalId) -> Unit,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            GoalsEffect.NavigateBack -> onNavigateBack()
            GoalsEffect.NavigateToGoalCreation -> onNavigateToGoalCreation()
            is GoalsEffect.NavigateToGoalDetails -> onNavigateToGoalDetails(effect.goalId)
        }
    }

    GoalsContent(
        goals = (state as? AsyncState.Content)?.value?.goals.orEmpty(),
        isLoading = state is AsyncState.Loading,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun GoalsContent(
    goals: List<GoalListItem>,
    isLoading: Boolean,
    onEvent: (GoalsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.goals_title),
                onBack = { onEvent(GoalsEvent.OnBackClick) },
            )
        },
        floatingActionButton = {
            val addLabel = stringResource(Res.string.goals_add)
            SurferAddFab(
                label = addLabel,
                onClick = { onEvent(GoalsEvent.OnAddGoalClick) },
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize().padding(padding))
            goals.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                SurferEmptyState(
                    icon = SurferIcons.Savings,
                    title = stringResource(Res.string.goals_empty_title),
                    subtitle = stringResource(Res.string.goals_empty_subtitle),
                    actionLabel = stringResource(Res.string.goals_add),
                    onActionClick = { onEvent(GoalsEvent.OnAddGoalClick) },
                )
            }

            else -> GoalsList(goals = goals, padding = padding, onEvent = onEvent)
        }
    }
}

@Composable
private fun GoalsList(
    goals: List<GoalListItem>,
    padding: PaddingValues,
    onEvent: (GoalsEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(
            start = AppTheme.spacing.default,
            end = AppTheme.spacing.default,
            top = AppTheme.spacing.small,
            bottom = AppTheme.spacing.xxLarge,
        ),
    ) {
        items(goals, key = { it.id.value }) { goal ->
            Box(modifier = Modifier.padding(bottom = AppTheme.spacing.medium)) {
                SurferGoalCard(
                    title = goal.title,
                    emoji = goal.emoji,
                    hue = goal.hue,
                    status = goal.status.toUi(),
                    statusLabel = stringResource(goal.status.labelRes()),
                    progress = goal.progress,
                    savedLabel = goal.savedLabel,
                    targetLabel = goal.targetLabel,
                    percentLabel = goal.percentLabel,
                    caption = goal.targetDate?.let {
                        stringResource(Res.string.goals_card_deadline, formatGoalDate(it))
                    },
                    onClick = { onEvent(GoalsEvent.OnGoalClick(goal.id)) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun GoalsScreenPreview() {
    SurferComponentPreview {
        GoalsContent(
            goals = listOf(
                GoalListItem(
                    id = GoalId("1"),
                    title = "Emergency fund",
                    emoji = "☂️",
                    hue = 210,
                    status = GoalStatus.ACTIVE,
                    targetDate = LocalDate(2026, 8, 1),
                    progress = 0.74f,
                    percentLabel = "74%",
                    savedLabel = "€8,915",
                    targetLabel = "€12,000",
                ),
            ),
            isLoading = false,
            onEvent = {},
        )
    }
}
