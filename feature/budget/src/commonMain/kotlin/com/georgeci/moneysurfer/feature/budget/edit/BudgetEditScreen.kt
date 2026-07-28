package com.georgeci.moneysurfer.feature.budget.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.feature.budget.budgetPeriodLabel
import com.georgeci.moneysurfer.feature.budget.formatShortDate
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.components.budget.SurferCategoryChip
import com.georgeci.moneysurfer.uikit.components.budget.SurferCategoryChipGrid
import com.georgeci.moneysurfer.uikit.components.budget.SurferPercentStepper
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AmountInputTransformation
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budget_edit_alert_decrease
import moneysurfer.feature.budget.generated.resources.budget_edit_alert_increase
import moneysurfer.feature.budget.generated.resources.budget_edit_alert_label
import moneysurfer.feature.budget.generated.resources.budget_edit_amount_label
import moneysurfer.feature.budget.generated.resources.budget_edit_amount_required
import moneysurfer.feature.budget.generated.resources.budget_edit_categories_hint
import moneysurfer.feature.budget.generated.resources.budget_edit_categories_label
import moneysurfer.feature.budget.generated.resources.budget_edit_name_label
import moneysurfer.feature.budget.generated.resources.budget_edit_name_required
import moneysurfer.feature.budget.generated.resources.budget_edit_period_label
import moneysurfer.feature.budget.generated.resources.budget_edit_rollover_label
import moneysurfer.feature.budget.generated.resources.budget_edit_rollover_supporting
import moneysurfer.feature.budget.generated.resources.budget_edit_save
import moneysurfer.feature.budget.generated.resources.budget_edit_start_date_label
import moneysurfer.feature.budget.generated.resources.budget_edit_title_create
import moneysurfer.feature.budget.generated.resources.budget_edit_title_edit
import moneysurfer.feature.budget.generated.resources.budgets_all_categories
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the budget create/edit screen — see docs/testing/testing-strategy.md. */
object BudgetEditTestTags {
    const val Root = "budgetEdit:root"
    const val NameField = "budgetEdit:name"
    const val AmountField = "budgetEdit:amount"
    const val SaveButton = "budgetEdit:save"
}

@Composable
fun BudgetEditScreen(
    budgetId: BudgetId? = null,
    onNavigateBack: () -> Unit,
    viewModel: BudgetEditViewModel = koinViewModel(
        key = budgetId?.value ?: "new",
    ) { parametersOf(budgetId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            BudgetEditEffect.NavigateBack -> onNavigateBack()
        }
    }

    BudgetEditContent(
        state = state,
        isEditing = budgetId != null,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun BudgetEditContent(
    state: BudgetEditState,
    isEditing: Boolean,
    onEvent: (BudgetEditEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(BudgetEditTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(
                    if (isEditing) Res.string.budget_edit_title_edit else Res.string.budget_edit_title_create,
                ),
                onBack = { onEvent(BudgetEditEvent.OnBackClick) },
                actions = {
                    SurferToolbarButtonAction(
                        icon = SurferIcons.Check,
                        text = stringResource(Res.string.budget_edit_save),
                        onClick = { onEvent(BudgetEditEvent.OnSaveClick) },
                        enabled = state.canSave,
                        modifier = Modifier
                            .testTag(BudgetEditTestTags.SaveButton),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .surferContentContainer()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.default, vertical = AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.default),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(BudgetEditEvent.OnNameChanged(it)) },
                label = { Text(stringResource(Res.string.budget_edit_name_label)) },
                isError = state.nameMissing,
                supportingText = if (state.nameMissing) {
                    { Text(stringResource(Res.string.budget_edit_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BudgetEditTestTags.NameField),
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = { input ->
                    AmountInputTransformation.validateAndNormalize(input)?.let {
                        onEvent(BudgetEditEvent.OnAmountChanged(it))
                    }
                },
                label = { Text(stringResource(Res.string.budget_edit_amount_label)) },
                isError = state.amountMissing,
                supportingText = if (state.amountMissing) {
                    { Text(stringResource(Res.string.budget_edit_amount_required)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BudgetEditTestTags.AmountField),
            )

            SurferSectionLabel(stringResource(Res.string.budget_edit_period_label))
            SurferSegmentedControl(
                options = BudgetPeriod.entries,
                selected = state.period,
                label = { budgetPeriodLabel(it) },
                onSelect = { onEvent(BudgetEditEvent.OnPeriodChanged(it)) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.budget_edit_start_date_label),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatShortDate(state.startDate),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.materialColors.onSurface,
                )
            }

            SurferSectionLabel(stringResource(Res.string.budget_edit_categories_label))
            SurferCategoryChipGrid(
                categories = state.categoryOptions.map { category ->
                    val visual = SurferCategoryPalette.visualFor(
                        id = category.id,
                        iconKey = category.iconKey,
                        hue = category.hue,
                        systemKind = category.systemKind,
                    )
                    SurferCategoryChip(
                        id = category.id,
                        name = category.name,
                        icon = visual.icon,
                        tint = visual.tint,
                    )
                },
                selectedIds = state.selectedCategoryIds,
                allCategoriesLabel = stringResource(Res.string.budgets_all_categories),
                onToggle = { onEvent(BudgetEditEvent.OnCategoryToggled(it)) },
                onSelectAllCategories = { onEvent(BudgetEditEvent.OnAllCategoriesSelected) },
            )
            Text(
                text = stringResource(Res.string.budget_edit_categories_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.budget_edit_alert_label),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SurferPercentStepper(
                    value = state.alertPercent,
                    onValueChange = { onEvent(BudgetEditEvent.OnAlertPercentChanged(it)) },
                    decreaseContentDescription = stringResource(Res.string.budget_edit_alert_decrease),
                    increaseContentDescription = stringResource(Res.string.budget_edit_alert_increase),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.budget_edit_rollover_label),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.materialColors.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.budget_edit_rollover_supporting),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.rollover,
                    onCheckedChange = { onEvent(BudgetEditEvent.OnRolloverChanged(it)) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun BudgetEditContentPreview() {
    AppTheme {
        BudgetEditContent(
            state = BudgetEditState(name = "Groceries", amount = "400"),
            isEditing = false,
            onEvent = {},
        )
    }
}
