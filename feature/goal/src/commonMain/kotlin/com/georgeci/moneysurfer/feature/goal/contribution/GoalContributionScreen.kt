package com.georgeci.moneysurfer.feature.goal.contribution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.feature.goal.GoalDateField
import com.georgeci.moneysurfer.feature.goal.GoalDatePickerDialog
import com.georgeci.moneysurfer.feature.goal.formatGoalDate
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.LocalDate
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_contribution_amount_label
import moneysurfer.feature.goal.generated.resources.goal_contribution_date_label
import moneysurfer.feature.goal.generated.resources.goal_contribution_note_label
import moneysurfer.feature.goal.generated.resources.goal_contribution_save
import moneysurfer.feature.goal.generated.resources.goal_contribution_saved_label
import moneysurfer.feature.goal.generated.resources.goal_contribution_title_add
import moneysurfer.feature.goal.generated.resources.goal_contribution_title_withdraw
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the goal add/withdraw screen — see docs/testing/testing-strategy.md. */
object GoalContributionTestTags {
    const val Root = "goalContribution:root"
    const val AmountField = "goalContribution:amount"
    const val SaveButton = "goalContribution:save"
}

@Composable
fun GoalContributionScreen(
    goalId: GoalId,
    mode: GoalContributionMode,
    onNavigateBack: () -> Unit,
    viewModel: GoalContributionViewModel = koinViewModel(
        key = "${goalId.value}-${mode.name}",
    ) { parametersOf(goalId, mode) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            GoalContributionEffect.NavigateBack -> onNavigateBack()
        }
    }

    GoalContributionContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun GoalContributionContent(
    state: GoalContributionState,
    onEvent: (GoalContributionEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(GoalContributionTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(
                    when (state.mode) {
                        GoalContributionMode.ADD -> Res.string.goal_contribution_title_add
                        GoalContributionMode.WITHDRAW -> Res.string.goal_contribution_title_withdraw
                    },
                ),
                onBack = { onEvent(GoalContributionEvent.OnBackClick) },
                actions = {
                    SurferToolbarButtonAction(
                        icon = SurferIcons.Check,
                        text = stringResource(Res.string.goal_contribution_save),
                        enabled = state.canSave,
                        onClick = { onEvent(GoalContributionEvent.OnSaveClick) },
                        modifier = Modifier.testTag(GoalContributionTestTags.SaveButton),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.default),
        ) {
            Text(
                text = stringResource(Res.string.goal_contribution_saved_label, state.savedLabel),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = { onEvent(GoalContributionEvent.OnAmountChanged(it)) },
                label = { Text(stringResource(Res.string.goal_contribution_amount_label)) },
                isError = state.error != null,
                supportingText = state.error?.let { error -> { Text(stringResource(error)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GoalContributionTestTags.AmountField),
            )

            GoalDateField(
                label = stringResource(Res.string.goal_contribution_date_label),
                value = state.occurredOn?.let(::formatGoalDate).orEmpty(),
                onClick = { onEvent(GoalContributionEvent.OnDatePickerOpen) },
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = { onEvent(GoalContributionEvent.OnNoteChanged(it)) },
                label = { Text(stringResource(Res.string.goal_contribution_note_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.showDatePicker) {
        GoalDatePickerDialog(
            initialDate = state.occurredOn,
            onDismiss = { onEvent(GoalContributionEvent.OnDatePickerDismiss) },
            onConfirm = {
                onEvent(GoalContributionEvent.OnDateChanged(it))
                onEvent(GoalContributionEvent.OnDatePickerDismiss)
            },
        )
    }
}

@Preview
@Composable
private fun GoalContributionScreenPreview() {
    SurferComponentPreview {
        GoalContributionContent(
            state = GoalContributionState(
                mode = GoalContributionMode.ADD,
                goalTitle = "Emergency fund",
                savedLabel = "€8,915",
                amount = "250",
                occurredOn = LocalDate(2026, 7, 12),
            ),
            onEvent = {},
        )
    }
}
