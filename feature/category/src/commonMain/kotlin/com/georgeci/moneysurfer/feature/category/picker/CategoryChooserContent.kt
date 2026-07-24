package com.georgeci.moneysurfer.feature.category.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.uikit.components.SurferSearchField
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferBottomSheetPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_creation_close_content_description
import moneysurfer.feature.category.generated.resources.category_creation_type_expense
import moneysurfer.feature.category.generated.resources.category_creation_type_income
import moneysurfer.feature.category.generated.resources.category_creation_type_transfer
import moneysurfer.feature.category.generated.resources.category_picker_currently
import moneysurfer.feature.category.generated.resources.category_picker_show_grid
import moneysurfer.feature.category.generated.resources.category_picker_show_tree
import moneysurfer.feature.category.generated.resources.transaction_creation_category_search
import moneysurfer.feature.category.generated.resources.transaction_creation_category_sheet_title
import org.jetbrains.compose.resources.stringResource

/** The title block sits one step wider than the body rows below it, as the artboard has it. */
private val SheetHeaderPadding = 20.dp

/** Shared with both variant bodies so the grid, the cards and the search field line up. */
internal val SheetBodyPadding = 16.dp
private val TabHeight = 36.dp
private val TabRadius = 18.dp
private val TabGlyphSize = 16.dp
private val TabSpacing = 8.dp
private val TabLabelSpacing = 6.dp
private val SublineSpacing = 2.dp
private val ChromeSpacing = 12.dp
private val SheetBottomPadding = 16.dp

/**
 * Everything the category-chooser sheet draws below the drag handle: the title block with the
 * `Currently: …` subline and a close action, the Expense / Income / Transfer tab row, the search
 * field, and then whichever of the two designed bodies [CategoryChooserState.Content.variant]
 * asks for.
 *
 * The sheet shell itself — 28dp top corners, `surfaceContainerLow`, the 32×4 handle — comes from
 * the `ModalBottomSheet` that `BottomSheetSceneStrategy` wraps this entry in, so it is
 * deliberately not redrawn here.
 */
@Composable
internal fun CategoryChooserContent(
    state: CategoryChooserState.Content,
    onEvent: (CategoryChooserEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = SheetBottomPadding),
    ) {
        ChooserHeader(
            selectedName = state.selectedName,
            variant = state.variant,
            onToggleVariant = { onEvent(CategoryChooserEvent.OnVariantToggle) },
            onClose = { onEvent(CategoryChooserEvent.OnDismiss) },
        )

        if (!state.typeLocked) {
            Spacer(Modifier.height(ChromeSpacing))
            TypeTabRow(
                selected = state.type,
                onSelect = { onEvent(CategoryChooserEvent.OnTypeSelected(it)) },
            )
        }

        Spacer(Modifier.height(ChromeSpacing))
        SurferSearchField(
            value = state.query,
            onValueChange = { onEvent(CategoryChooserEvent.OnQueryChange(it)) },
            placeholder = stringResource(Res.string.transaction_creation_category_search),
            containerColor = AppTheme.materialColors.surfaceContainerHighest,
        )
        Spacer(Modifier.height(ChromeSpacing))

        when (state.variant) {
            CategoryPickerVariant.GRID -> CategoryPickerGrid(
                items = state.gridItems,
                onSelect = { onEvent(CategoryChooserEvent.OnCategorySelected(it)) },
                onCreateNew = { onEvent(CategoryChooserEvent.OnCreateNewCategoryClick) },
            )
            CategoryPickerVariant.TREE -> CategoryPickerTree(
                groups = state.treeItems,
                onSelect = { onEvent(CategoryChooserEvent.OnCategorySelected(it)) },
                onToggleExpand = { onEvent(CategoryChooserEvent.OnParentExpandToggle(it)) },
                onCreateNew = { onEvent(CategoryChooserEvent.OnCreateNewCategoryClick) },
            )
        }
    }
}

@Composable
private fun ChooserHeader(
    selectedName: String?,
    variant: CategoryPickerVariant,
    onToggleVariant: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SheetHeaderPadding, end = SheetBodyPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.transaction_creation_category_sheet_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
            // Only meaningful once something is picked — an empty "Currently:" reads as a bug.
            if (selectedName != null) {
                Spacer(Modifier.height(SublineSpacing))
                Text(
                    text = stringResource(Res.string.category_picker_currently, selectedName),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
        // Shows the layout it switches *to*, so the glyph reads as the destination rather than
        // as a label for what is already on screen.
        IconButton(onClick = onToggleVariant) {
            Icon(
                imageVector = when (variant) {
                    CategoryPickerVariant.GRID -> SurferIcons.ListView
                    CategoryPickerVariant.TREE -> SurferIcons.GridView
                },
                contentDescription = stringResource(
                    when (variant) {
                        CategoryPickerVariant.GRID -> Res.string.category_picker_show_tree
                        CategoryPickerVariant.TREE -> Res.string.category_picker_show_grid
                    },
                ),
                tint = AppTheme.materialColors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = SurferIcons.Close,
                contentDescription = stringResource(
                    Res.string.category_creation_close_content_description,
                ),
                tint = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeTabRow(
    selected: CategoryType,
    onSelect: (CategoryType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SheetBodyPadding),
        horizontalArrangement = Arrangement.spacedBy(TabSpacing),
    ) {
        CategoryType.entries.forEach { type ->
            TypeTab(
                label = categoryTypeLabel(type),
                glyph = typeGlyph(type),
                selected = type == selected,
                onClick = { onSelect(type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TypeTab(
    label: String,
    glyph: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TabRadius)
    val content = if (selected) {
        AppTheme.materialColors.onSecondaryContainer
    } else {
        AppTheme.materialColors.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .height(TabHeight)
            .clip(shape)
            .background(
                if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent,
            )
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, AppTheme.materialColors.outlineVariant, shape)
                },
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = glyph,
            // decorative — the tab label right next to it carries the meaning
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(TabGlyphSize),
        )
        Spacer(Modifier.width(TabLabelSpacing))
        Text(text = label, style = AppTheme.typography.labelLarge, color = content)
    }
}

private fun typeGlyph(type: CategoryType): ImageVector = when (type) {
    CategoryType.EXPENSE -> SurferIcons.ArrowDown
    CategoryType.INCOME -> SurferIcons.ArrowUp
    CategoryType.TRANSFER -> SurferIcons.SwapHoriz
}

@Composable
private fun categoryTypeLabel(type: CategoryType): String = when (type) {
    CategoryType.EXPENSE -> stringResource(Res.string.category_creation_type_expense)
    CategoryType.INCOME -> stringResource(Res.string.category_creation_type_income)
    CategoryType.TRANSFER -> stringResource(Res.string.category_creation_type_transfer)
}

private fun previewItem(
    id: String,
    name: String,
    iconKey: String,
    hue: Int,
    selected: Boolean = false,
) = CategoryPickerItem(
    id = CategoryId(id),
    name = name,
    iconKey = iconKey,
    hue = hue,
    systemKind = null,
    selected = selected,
)

private val previewGridItems = listOf(
    previewItem("groceries", "Groceries", "cash", 162, selected = true),
    previewItem("dining", "Dining", "receipt", 35),
    previewItem("transport", "Transport", "card", 258),
    previewItem("rent", "Rent", "wallet", 303),
    previewItem("fuel", "Fuel", "tag", 68),
    previewItem("pharmacy", "Pharmacy", "event", 340),
)

private val previewTreeGroups = listOf(
    CategoryTreeGroup(
        parent = previewItem("food", "Food", "cash", 162),
        childCount = 2,
        expanded = true,
        children = listOf(
            previewItem("groceries", "Groceries", "cash", 162, selected = true),
            previewItem("dining", "Dining", "receipt", 35),
        ),
    ),
    CategoryTreeGroup(
        parent = previewItem("transport", "Transport", "card", 258),
        childCount = 3,
    ),
    CategoryTreeGroup(parent = previewItem("rent", "Rent", "wallet", 303)),
)

private fun previewState(variant: CategoryPickerVariant) = CategoryChooserState.Content(
    selectedId = CategoryId("groceries"),
    selectedName = "Groceries",
    type = CategoryType.EXPENSE,
    typeLocked = false,
    query = "",
    expandedIds = setOf(CategoryId("food")),
    variant = variant,
    gridItems = previewGridItems,
    treeItems = previewTreeGroups,
)

@Preview
@Composable
private fun CategoryChooserGridPreview() {
    SurferBottomSheetPreview {
        CategoryChooserContent(state = previewState(CategoryPickerVariant.GRID), onEvent = {})
    }
}

@Preview
@Composable
private fun CategoryChooserTreePreview() {
    SurferBottomSheetPreview {
        CategoryChooserContent(state = previewState(CategoryPickerVariant.TREE), onEvent = {})
    }
}

@Preview
@Composable
private fun CategoryChooserGridDarkPreview() {
    SurferBottomSheetPreview(darkTheme = true) {
        CategoryChooserContent(state = previewState(CategoryPickerVariant.GRID), onEvent = {})
    }
}

@Preview
@Composable
private fun CategoryChooserTreeDarkPreview() {
    SurferBottomSheetPreview(darkTheme = true) {
        CategoryChooserContent(state = previewState(CategoryPickerVariant.TREE), onEvent = {})
    }
}
