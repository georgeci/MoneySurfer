package com.georgeci.moneysurfer.feature.goal.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.feature.goal.formatGoalDate
import com.georgeci.moneysurfer.feature.goal.labelRes
import com.georgeci.moneysurfer.feature.goal.toUi
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalContributionRow
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalIcon
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalProgressRing
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalStatusPill
import com.georgeci.moneysurfer.uikit.components.goal.goalAccentColor
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AsyncState
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.LocalDate
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_details_actions
import moneysurfer.feature.goal.generated.resources.goal_details_add
import moneysurfer.feature.goal.generated.resources.goal_details_archive
import moneysurfer.feature.goal.generated.resources.goal_details_delete
import moneysurfer.feature.goal.generated.resources.goal_details_delete_cancel
import moneysurfer.feature.goal.generated.resources.goal_details_delete_confirm
import moneysurfer.feature.goal.generated.resources.goal_details_delete_message
import moneysurfer.feature.goal.generated.resources.goal_details_delete_title
import moneysurfer.feature.goal.generated.resources.goal_details_edit
import moneysurfer.feature.goal.generated.resources.goal_details_history
import moneysurfer.feature.goal.generated.resources.goal_details_history_empty
import moneysurfer.feature.goal.generated.resources.goal_details_linked_account
import moneysurfer.feature.goal.generated.resources.goal_details_linked_account_note
import moneysurfer.feature.goal.generated.resources.goal_details_of_target
import moneysurfer.feature.goal.generated.resources.goal_details_pause
import moneysurfer.feature.goal.generated.resources.goal_details_reached
import moneysurfer.feature.goal.generated.resources.goal_details_remaining
import moneysurfer.feature.goal.generated.resources.goal_details_resume
import moneysurfer.feature.goal.generated.resources.goal_details_unarchive
import moneysurfer.feature.goal.generated.resources.goal_details_withdraw
import moneysurfer.feature.goal.generated.resources.goals_card_deadline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the Goal details screen — see docs/testing/testing-strategy.md. */
object GoalDetailsTestTags {
    const val Root = "goalDetails:root"
    const val AddMoneyButton = "goalDetails:addMoney"
    const val HistoryHeader = "goalDetails:history"
}

@Composable
fun GoalDetailsScreen(
    goalId: GoalId,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (GoalId) -> Unit,
    onNavigateToContribution: (GoalId, GoalContributionMode) -> Unit,
    viewModel: GoalDetailsViewModel = koinViewModel(key = goalId.value) { parametersOf(goalId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            GoalDetailsEffect.NavigateBack -> onNavigateBack()
            is GoalDetailsEffect.NavigateToEdit -> onNavigateToEdit(effect.goalId)
            is GoalDetailsEffect.NavigateToContribution ->
                onNavigateToContribution(effect.goalId, effect.mode)
        }
    }

    GoalDetailsScaffold(
        content = (state as? AsyncState.Content)?.value,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun GoalDetailsScaffold(
    content: GoalDetailsContent?,
    onEvent: (GoalDetailsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(GoalDetailsTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = content?.title.orEmpty(),
                onBack = { onEvent(GoalDetailsEvent.OnBackClick) },
                actions = {
                    IconButton(onClick = { onEvent(GoalDetailsEvent.OnActionsClick) }) {
                        Icon(
                            imageVector = SurferIcons.MoreVert,
                            contentDescription = stringResource(Res.string.goal_details_actions),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (content == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        GoalDetailsBody(content = content, padding = padding, onEvent = onEvent)

        if (content.showActions) {
            GoalActionsSheet(content = content, onEvent = onEvent)
        }
        if (content.showDeleteConfirmation) {
            DeleteGoalDialog(title = content.title, onEvent = onEvent)
        }
    }
}

@Composable
private fun GoalDetailsBody(
    content: GoalDetailsContent,
    padding: PaddingValues,
    onEvent: (GoalDetailsEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(
            horizontal = AppTheme.spacing.default,
            vertical = AppTheme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        item { GoalHero(content = content) }
        item { GoalActionsRow(content = content, onEvent = onEvent) }
        if (content.accountName != null) {
            item { LinkedAccountNote(accountName = content.accountName) }
        }
        item {
            Text(
                text = stringResource(Res.string.goal_details_history),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.testTag(GoalDetailsTestTags.HistoryHeader),
            )
        }
        if (content.contributions.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.goal_details_history_empty),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
        items(content.contributions, key = { it.id.value }) { contribution ->
            SurferGoalContributionRow(
                amountLabel = contribution.amountLabel,
                dateLabel = formatGoalDate(contribution.occurredOn),
                isWithdrawal = contribution.isWithdrawal,
                note = contribution.note,
            )
        }
    }
}

@Composable
private fun GoalHero(content: GoalDetailsContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        SurferGoalProgressRing(
            progress = content.progress,
            percentLabel = content.percentLabel,
            savedLabel = content.savedLabel,
            targetLabel = stringResource(Res.string.goal_details_of_target, content.targetLabel),
            color = goalAccentColor(content.hue),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            SurferGoalIcon(emoji = content.emoji, hue = content.hue)
            SurferGoalStatusPill(
                status = content.status.toUi(),
                label = stringResource(content.status.labelRes()),
            )
        }
        Text(
            text = if (content.isReached) {
                stringResource(Res.string.goal_details_reached)
            } else {
                stringResource(Res.string.goal_details_remaining, content.remainingLabel)
            },
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (content.targetDate != null) {
            Text(
                text = stringResource(Res.string.goals_card_deadline, formatGoalDate(content.targetDate)),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalActionsRow(
    content: GoalDetailsContent,
    onEvent: (GoalDetailsEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Button(
            onClick = { onEvent(GoalDetailsEvent.OnAddMoneyClick) },
            enabled = content.status == GoalStatus.ACTIVE || content.status == GoalStatus.COMPLETED,
            modifier = Modifier
                .weight(1f)
                .testTag(GoalDetailsTestTags.AddMoneyButton),
        ) {
            Text(stringResource(Res.string.goal_details_add))
        }
        OutlinedButton(
            onClick = { onEvent(GoalDetailsEvent.OnWithdrawClick) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(Res.string.goal_details_withdraw))
        }
    }
}

@Composable
private fun LinkedAccountNote(accountName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxSmall)) {
        Text(
            text = stringResource(Res.string.goal_details_linked_account, accountName),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurface,
        )
        Text(
            text = stringResource(Res.string.goal_details_linked_account_note),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalActionsSheet(
    content: GoalDetailsContent,
    onEvent: (GoalDetailsEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(GoalDetailsEvent.OnActionsDismiss) },
        containerColor = AppTheme.materialColors.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.large)) {
            SheetAction(
                label = stringResource(Res.string.goal_details_edit),
                onClick = { onEvent(GoalDetailsEvent.OnEditClick) },
            )
            SheetAction(
                label = if (content.status == GoalStatus.PAUSED) {
                    stringResource(Res.string.goal_details_resume)
                } else {
                    stringResource(Res.string.goal_details_pause)
                },
                onClick = { onEvent(GoalDetailsEvent.OnPauseResumeClick) },
            )
            SheetAction(
                label = if (content.status == GoalStatus.ARCHIVED) {
                    stringResource(Res.string.goal_details_unarchive)
                } else {
                    stringResource(Res.string.goal_details_archive)
                },
                onClick = { onEvent(GoalDetailsEvent.OnArchiveClick) },
            )
            SheetAction(
                label = stringResource(Res.string.goal_details_delete),
                onClick = { onEvent(GoalDetailsEvent.OnDeleteClick) },
                destructive = true,
            )
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyLarge,
            color = if (destructive) AppTheme.materialColors.error else AppTheme.materialColors.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun DeleteGoalDialog(
    title: String,
    onEvent: (GoalDetailsEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(GoalDetailsEvent.OnDeleteCancel) },
        title = { Text(stringResource(Res.string.goal_details_delete_title)) },
        text = { Text(stringResource(Res.string.goal_details_delete_message, title)) },
        confirmButton = {
            TextButton(onClick = { onEvent(GoalDetailsEvent.OnDeleteConfirm) }) {
                Text(
                    text = stringResource(Res.string.goal_details_delete_confirm),
                    color = AppTheme.materialColors.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(GoalDetailsEvent.OnDeleteCancel) }) {
                Text(stringResource(Res.string.goal_details_delete_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun GoalDetailsScreenPreview() {
    SurferComponentPreview {
        GoalDetailsScaffold(
            content = GoalDetailsContent(
                title = "Emergency fund",
                emoji = "☂️",
                hue = 210,
                status = GoalStatus.ACTIVE,
                targetDate = LocalDate(2026, 8, 1),
                accountName = "Savings",
                progress = 0.74f,
                percentLabel = "74%",
                savedLabel = "€8,915",
                targetLabel = "€12,000",
                remainingLabel = "€3,085",
                isReached = false,
                contributions = listOf(
                    GoalContributionUi(
                        id = GoalContributionId("c-1"),
                        amountLabel = "+€250",
                        occurredOn = LocalDate(2026, 7, 12),
                        note = "Salary top-up",
                        isWithdrawal = false,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
