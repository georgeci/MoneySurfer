package com.georgeci.moneysurfer.feature.category.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_creation_add_another
import moneysurfer.feature.category.generated.resources.category_creation_color_label
import moneysurfer.feature.category.generated.resources.category_creation_extra_label
import moneysurfer.feature.category.generated.resources.category_creation_extra_optional
import moneysurfer.feature.category.generated.resources.category_creation_field_cap
import moneysurfer.feature.category.generated.resources.category_creation_field_cap_helper
import moneysurfer.feature.category.generated.resources.category_creation_field_note
import moneysurfer.feature.category.generated.resources.category_creation_field_parent
import moneysurfer.feature.category.generated.resources.category_creation_icon_label
import moneysurfer.feature.category.generated.resources.category_creation_name_label
import moneysurfer.feature.category.generated.resources.category_creation_remove
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
// TODO refactor: lots of magic numbers
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
    val icons = SurferCategoryPalette.icons
    val tints = SurferCategoryPalette.tints
    val selectedIcon = icons[state.selectedIconIndex.coerceIn(0, icons.lastIndex)]
    val selectedTint = tints[state.selectedColorIndex.coerceIn(0, tints.lastIndex)]

    Scaffold(
        modifier = Modifier.surferSafeInsets(),
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
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
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
                extraSuffix = if (state.showMonthlyCap && state.monthlyCap.isNotBlank()) {
                    " · Capped at ${state.monthlyCap}"
                } else {
                    ""
                },
                icon = selectedIcon,
                tint = selectedTint,
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
                modifier = Modifier.fillMaxWidth(),
            )

            if (ShowIconPicker) {
                IconGrid(
                    icons = icons,
                    selectedIndex = state.selectedIconIndex,
                    tint = selectedTint,
                    onSelect = { onEvent(CategoryCreationEvent.OnIconSelected(it)) },
                )
            }

            if (ShowColorPicker) {
                ColorGrid(
                    tints = tints,
                    selectedIndex = state.selectedColorIndex,
                    onSelect = { onEvent(CategoryCreationEvent.OnColorSelected(it)) },
                )
            }

            if (ShowExtraDetails) {
                ExtraDetails(state = state, onEvent = onEvent)
            }

            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }
}

@Composable
private fun PreviewCard(
    name: String,
    typeLabel: String,
    extraSuffix: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            modifier = Modifier.padding(16.dp),
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
                    text = typeLabel + extraSuffix,
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
        Text(
            text = stringResource(Res.string.category_creation_type_label),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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

@Composable
private fun IconGrid(
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>,
    selectedIndex: Int,
    tint: Color,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            text = stringResource(Res.string.category_creation_icon_label),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val rows = icons.chunked(6)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIndex, icon ->
                        val index = rowIndex * 6 + colIndex
                        val selected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) tint.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) tint else AppTheme.materialColors.outlineVariant,
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { onSelect(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) tint else AppTheme.materialColors.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    repeat(6 - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorGrid(
    tints: List<Color>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            text = stringResource(Res.string.category_creation_color_label),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tints.forEachIndexed { index, color ->
                val selected = index == selectedIndex
                val border = if (selected) {
                    Modifier
                        .border(3.dp, AppTheme.materialColors.surface, CircleShape)
                        .border(5.dp, color, CircleShape)
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f))
                        .then(border)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = SurferIcons.Check,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraDetails(
    state: CategoryCreationState,
    onEvent: (CategoryCreationEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.category_creation_extra_label),
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.category_creation_extra_optional),
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }

        if (state.showMonthlyCap) {
            FieldWithRemove(
                onRemove = {
                    onEvent(
                        CategoryCreationEvent.OnToggleExtraField(CategoryCreationExtraField.MonthlyCap, false),
                    )
                },
            ) {
                OutlinedTextField(
                    value = state.monthlyCap,
                    onValueChange = { onEvent(CategoryCreationEvent.OnMonthlyCapChanged(it)) },
                    label = { Text(stringResource(Res.string.category_creation_field_cap)) },
                    supportingText = { Text(stringResource(Res.string.category_creation_field_cap_helper)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.showParent) {
            FieldWithRemove(
                onRemove = {
                    onEvent(
                        CategoryCreationEvent.OnToggleExtraField(CategoryCreationExtraField.Parent, false),
                    )
                },
            ) {
                OutlinedTextField(
                    value = state.parent,
                    onValueChange = { onEvent(CategoryCreationEvent.OnParentChanged(it)) },
                    label = { Text(stringResource(Res.string.category_creation_field_parent)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.showNote) {
            FieldWithRemove(
                onRemove = {
                    onEvent(
                        CategoryCreationEvent.OnToggleExtraField(CategoryCreationExtraField.Note, false),
                    )
                },
            ) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onEvent(CategoryCreationEvent.OnNoteChanged(it)) },
                    label = { Text(stringResource(Res.string.category_creation_field_note)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val availableChips = buildList {
            if (!state.showMonthlyCap) {
                add(
                    CategoryCreationExtraField.MonthlyCap to Res.string.category_creation_field_cap,
                )
            }
            if (!state.showParent) add(CategoryCreationExtraField.Parent to Res.string.category_creation_field_parent)
            if (!state.showNote) add(CategoryCreationExtraField.Note to Res.string.category_creation_field_note)
        }
        if (availableChips.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(Res.string.category_creation_add_another),
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableChips.forEach { (field, labelRes) ->
                        AddChip(
                            label = stringResource(labelRes),
                            onClick = { onEvent(CategoryCreationEvent.OnToggleExtraField(field, true)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldWithRemove(
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onRemove,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = SurferIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.category_creation_remove), style = AppTheme.typography.labelMedium)
            }
        }
        content()
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, AppTheme.materialColors.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Add,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Text(label, style = AppTheme.typography.labelMedium)
    }
}

@Preview
@Composable
private fun CategoryCreationPreview() {
    AppTheme {
        CategoryCreationContent(
            state = CategoryCreationState(name = "Coffee", showMonthlyCap = true, monthlyCap = "120.00"),
            isEditing = false,
            onEvent = {},
        )
    }
}

private const val ShowIconPicker = false
private const val ShowColorPicker = false
private const val ShowExtraDetails = false
