package com.georgeci.moneysurfer.feature.category.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferDropdown
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.components.category.SurferColorSwatchRow
import com.georgeci.moneysurfer.uikit.components.category.SurferIconPickerGrid
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AmountInputTransformation
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_creation_cap_hint
import moneysurfer.feature.category.generated.resources.category_creation_cap_label
import moneysurfer.feature.category.generated.resources.category_creation_cap_managed
import moneysurfer.feature.category.generated.resources.category_creation_cap_managed_hint
import moneysurfer.feature.category.generated.resources.category_creation_color_label
import moneysurfer.feature.category.generated.resources.category_creation_field_parent
import moneysurfer.feature.category.generated.resources.category_creation_icon_label
import moneysurfer.feature.category.generated.resources.category_creation_name_counter
import moneysurfer.feature.category.generated.resources.category_creation_name_error_required
import moneysurfer.feature.category.generated.resources.category_creation_name_label
import moneysurfer.feature.category.generated.resources.category_creation_parent_none
import moneysurfer.feature.category.generated.resources.category_creation_save
import moneysurfer.feature.category.generated.resources.category_creation_title_create
import moneysurfer.feature.category.generated.resources.category_creation_title_edit
import moneysurfer.feature.category.generated.resources.category_creation_type_expense
import moneysurfer.feature.category.generated.resources.category_creation_type_income
import moneysurfer.feature.category.generated.resources.category_creation_type_label
import moneysurfer.feature.category.generated.resources.category_creation_untitled
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val SectionSpacing = 24.dp

/** Stable selectors for the category create/edit screen — see docs/testing/testing-strategy.md. */
object CategoryCreationTestTags {
    const val Root = "categoryCreation:root"
    const val NameField = "categoryCreation:name"
    const val SaveButton = "categoryCreation:save"
}

@Composable
fun CategoryCreationScreen(
    categoryId: CategoryId? = null,
    onNavigateBack: () -> Unit,
    viewModel: CategoryCreationViewModel = koinViewModel(
        key = categoryId?.value ?: "new",
    ) { parametersOf(categoryId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            CategoryCreationEffect.NavigateBack -> onNavigateBack()
        }
    }

    CategoryCreationContent(
        state = state,
        isEditing = categoryId != null,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun CategoryCreationContent(
    state: CategoryCreationState,
    isEditing: Boolean,
    onEvent: (CategoryCreationEvent) -> Unit,
) {
    // Same resolver every other category bubble in the app goes through, so the preview card
    // shows exactly what the manage list and transaction screens will show once saved.
    val visual = SurferCategoryPalette.visualFor(
        id = "",
        iconKey = state.iconKey,
        hue = state.hue,
    )

    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(CategoryCreationTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(
                    if (isEditing) {
                        Res.string.category_creation_title_edit
                    } else {
                        Res.string.category_creation_title_create
                    },
                ),
                onBack = { onEvent(CategoryCreationEvent.OnBackClick) },
                actions = {
                    SurferToolbarButtonAction(
                        icon = SurferIcons.Check,
                        text = stringResource(Res.string.category_creation_save),
                        onClick = { onEvent(CategoryCreationEvent.OnSaveClick) },
                        enabled = state.canSave,
                        modifier = Modifier.testTag(CategoryCreationTestTags.SaveButton),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SectionSpacing)
                .padding(top = AppTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            PreviewCard(
                name = state.name.ifBlank { stringResource(Res.string.category_creation_untitled) },
                typeLabel = stringResource(
                    if (state.type == CategoryTypeUi.Income) {
                        Res.string.category_creation_type_income
                    } else {
                        Res.string.category_creation_type_expense
                    },
                ),
                icon = visual.icon,
                tint = visual.tint,
            )

            TypeSelector(
                selected = state.type,
                onChange = { onEvent(CategoryCreationEvent.OnTypeChanged(it)) },
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(CategoryCreationEvent.OnNameChanged(it)) },
                label = { Text(stringResource(Res.string.category_creation_name_label)) },
                singleLine = true,
                isError = state.nameMissing,
                supportingText = when {
                    state.nameMissing -> {
                        { Text(stringResource(Res.string.category_creation_name_error_required)) }
                    }
                    state.showNameCounter -> {
                        {
                            Text(
                                stringResource(
                                    Res.string.category_creation_name_counter,
                                    state.name.length,
                                    CategoryCreationState.NAME_MAX_LENGTH,
                                ),
                            )
                        }
                    }
                    else -> null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CategoryCreationTestTags.NameField),
            )

            Column {
                SurferSectionLabel(stringResource(Res.string.category_creation_icon_label))
                Spacer(Modifier.height(10.dp))
                SurferIconPickerGrid(
                    selectedKey = state.iconKey,
                    tint = visual.tint,
                    onSelect = { onEvent(CategoryCreationEvent.OnIconSelected(it)) },
                )
            }

            Column {
                SurferSectionLabel(stringResource(Res.string.category_creation_color_label))
                Spacer(Modifier.height(10.dp))
                SurferColorSwatchRow(
                    selectedHue = state.hue,
                    onSelect = { onEvent(CategoryCreationEvent.OnColorSelected(it)) },
                )
            }

            ParentPicker(
                selectedId = state.parentId,
                options = state.parentOptions,
                onSelect = { onEvent(CategoryCreationEvent.OnParentSelected(it)) },
            )

            if (state.showCap) {
                CapField(
                    cap = state.cap,
                    managedByBudgetName = state.capManagedByBudgetName,
                    onChange = { onEvent(CategoryCreationEvent.OnCapChanged(it)) },
                )
            }

            Spacer(Modifier.height(padding.calculateBottomPadding() + SectionSpacing))
        }
    }
}

@Composable
private fun PreviewCard(
    name: String,
    typeLabel: String,
    icon: ImageVector,
    tint: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(AppTheme.spacing.default),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SurferCategoryBubble(icon = icon, tint = tint, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = typeLabel,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TypeSelector(
    selected: CategoryTypeUi,
    onChange: (CategoryTypeUi) -> Unit,
) {
    Column {
        SurferSectionLabel(stringResource(Res.string.category_creation_type_label))
        Spacer(Modifier.height(AppTheme.spacing.small))
        SurferSegmentedControl(
            options = listOf(CategoryTypeUi.Expense, CategoryTypeUi.Income),
            selected = selected,
            label = { type ->
                stringResource(
                    when (type) {
                        CategoryTypeUi.Expense -> Res.string.category_creation_type_expense
                        CategoryTypeUi.Income -> Res.string.category_creation_type_income
                    },
                )
            },
            onSelect = onChange,
        )
    }
}

/**
 * Parent selection over the eligible options the ViewModel computed — the category itself, its
 * descendants, system rows and the wrong type are already gone from [options], so anything
 * offered here is safe to save.
 *
 * "None" is an entry in the list rather than a separate control: having no parent is an ordinary
 * choice for a top-level category, not the absence of one.
 */
@Composable
private fun ParentPicker(
    selectedId: CategoryId?,
    options: List<CategoryParentOption>,
    onSelect: (CategoryId?) -> Unit,
) {
    val noneLabel = stringResource(Res.string.category_creation_parent_none)
    val entries = listOf<CategoryParentOption?>(null) + options
    SurferDropdown(
        items = entries,
        selected = entries.firstOrNull { it?.id == selectedId },
        label = stringResource(Res.string.category_creation_field_parent),
        itemName = { it?.name ?: noneLabel },
        onItemSelected = { onSelect(it?.id) },
    )
}

/**
 * The monthly-cap shortcut. `Category` has no `cap` field — this is a two-tap front end for a
 * budget whose only category is this one (md/categories.md, decision 2). Clearing it deletes that
 * budget; leaving it empty never creates one.
 *
 * When a budget the user built on the Budgets screen already limits this category *alongside
 * others*, the field is replaced by a read-only notice naming that budget. Offering an editable
 * cap there would put two limits on one category with no defined winner, which is exactly what
 * keeping limits in Budgets was meant to avoid. The all-categories budget is not treated as such
 * coverage: it is the global envelope that per-category budgets already live inside.
 */
@Composable
private fun CapField(
    cap: String,
    managedByBudgetName: String?,
    onChange: (String) -> Unit,
) {
    if (managedByBudgetName != null) {
        Column {
            SurferSectionLabel(stringResource(Res.string.category_creation_cap_label))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.category_creation_cap_managed, managedByBudgetName),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.category_creation_cap_managed_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    } else {
        OutlinedTextField(
            value = cap,
            onValueChange = { input -> AmountInputTransformation.validateAndNormalize(input)?.let(onChange) },
            label = { Text(stringResource(Res.string.category_creation_cap_label)) },
            supportingText = { Text(stringResource(Res.string.category_creation_cap_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun CategoryCreationPreview() {
    AppTheme {
        CategoryCreationContent(
            state = CategoryCreationState(
                name = "Coffee",
                iconKey = SurferCategoryPalette.iconKeys[1],
                hue = SurferCategoryPalette.hues[1],
                parentOptions = listOf(CategoryParentOption(CategoryId("c-1"), "Dining")),
                parentId = CategoryId("c-1"),
                cap = "250",
            ),
            isEditing = false,
            onEvent = {},
        )
    }
}
