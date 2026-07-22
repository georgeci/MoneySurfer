package com.georgeci.moneysurfer.feature.goal.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.feature.goal.GoalDateField
import com.georgeci.moneysurfer.feature.goal.GoalDatePickerDialog
import com.georgeci.moneysurfer.feature.goal.formatGoalDate
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.components.goal.SurferEmojiPicker
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalHueRow
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalIcon
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.LocalDate
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_edit_color_label
import moneysurfer.feature.goal.generated.resources.goal_edit_icon_label
import moneysurfer.feature.goal.generated.resources.goal_edit_name_error_required
import moneysurfer.feature.goal.generated.resources.goal_edit_name_label
import moneysurfer.feature.goal.generated.resources.goal_edit_save
import moneysurfer.feature.goal.generated.resources.goal_edit_target_date_clear
import moneysurfer.feature.goal.generated.resources.goal_edit_target_date_label
import moneysurfer.feature.goal.generated.resources.goal_edit_target_date_placeholder
import moneysurfer.feature.goal.generated.resources.goal_edit_target_error
import moneysurfer.feature.goal.generated.resources.goal_edit_target_label
import moneysurfer.feature.goal.generated.resources.goal_edit_title_create
import moneysurfer.feature.goal.generated.resources.goal_edit_title_edit
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GoalEditScreen(
    goalId: GoalId? = null,
    onNavigateBack: () -> Unit,
    viewModel: GoalEditViewModel = koinViewModel(key = goalId?.value ?: "new") { parametersOf(goalId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            GoalEditEffect.NavigateBack -> onNavigateBack()
        }
    }

    GoalEditContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun GoalEditContent(
    state: GoalEditState,
    onEvent: (GoalEditEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(
                    if (state.isEdit) Res.string.goal_edit_title_edit else Res.string.goal_edit_title_create,
                ),
                onBack = { onEvent(GoalEditEvent.OnBackClick) },
                actions = {
                    SurferToolbarButtonAction(
                        icon = SurferIcons.Check,
                        text = stringResource(Res.string.goal_edit_save),
                        enabled = state.canSave,
                        onClick = { onEvent(GoalEditEvent.OnSaveClick) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.default),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            ) {
                SurferGoalIcon(emoji = state.emoji, hue = state.hue, size = 56.dp)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onEvent(GoalEditEvent.OnTitleChanged(it)) },
                    label = { Text(stringResource(Res.string.goal_edit_name_label)) },
                    isError = state.titleMissing,
                    supportingText = if (state.titleMissing) {
                        { Text(stringResource(Res.string.goal_edit_name_error_required)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.target,
                onValueChange = { onEvent(GoalEditEvent.OnTargetChanged(it)) },
                label = { Text(stringResource(Res.string.goal_edit_target_label)) },
                isError = state.targetMissing,
                supportingText = if (state.targetMissing) {
                    { Text(stringResource(Res.string.goal_edit_target_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            GoalDateField(
                label = stringResource(Res.string.goal_edit_target_date_label),
                value = state.targetDate?.let(::formatGoalDate)
                    ?: stringResource(Res.string.goal_edit_target_date_placeholder),
                onClick = { onEvent(GoalEditEvent.OnDatePickerOpen) },
                trailing = if (state.targetDate == null) {
                    null
                } else {
                    {
                        TextButton(onClick = { onEvent(GoalEditEvent.OnTargetDateCleared) }) {
                            Text(stringResource(Res.string.goal_edit_target_date_clear))
                        }
                    }
                },
            )

            SectionLabel(stringResource(Res.string.goal_edit_icon_label))
            SurferEmojiPicker(
                selected = state.emoji,
                onSelect = { onEvent(GoalEditEvent.OnEmojiSelected(it)) },
            )

            SectionLabel(stringResource(Res.string.goal_edit_color_label))
            SurferGoalHueRow(
                selected = state.hue,
                onSelect = { onEvent(GoalEditEvent.OnHueSelected(it)) },
            )
        }
    }

    if (state.showDatePicker) {
        GoalDatePickerDialog(
            initialDate = state.targetDate,
            onDismiss = { onEvent(GoalEditEvent.OnDatePickerDismiss) },
            onConfirm = {
                onEvent(GoalEditEvent.OnTargetDateChanged(it))
                onEvent(GoalEditEvent.OnDatePickerDismiss)
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.titleSmall,
        color = AppTheme.materialColors.onSurfaceVariant,
    )
}

@Preview
@Composable
private fun GoalEditScreenPreview() {
    SurferComponentPreview {
        GoalEditContent(
            state = GoalEditState(
                title = "Lisbon trip",
                emoji = "🏖️",
                hue = 30,
                target = "2400",
                targetDate = LocalDate(2026, 9, 1),
            ),
            onEvent = {},
        )
    }
}
