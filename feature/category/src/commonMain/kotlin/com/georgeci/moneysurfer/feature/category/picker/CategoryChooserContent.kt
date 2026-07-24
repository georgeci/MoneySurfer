package com.georgeci.moneysurfer.feature.category.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferActionCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferSearchField
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryCell
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferBottomSheetPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
// TODO refactor: lots of magic numbers
internal data class CategoryPickerRow(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val tint: Color,
    val isIncome: Boolean = false,
)

/**
 * Static content of the category-chooser bottom sheet. Renders the title, search field, the
 * "Create new" CTA, and a 4-column grid of category bubbles split into Expense / Income
 * sections. The hosting [Composable] provides the surrounding `ModalBottomSheet` chrome and
 * wires events through a ViewModel.
 */
@Composable
internal fun CategoryChooserContent(
    title: String,
    searchPlaceholder: String,
    categories: List<CategoryPickerRow>,
    selectedId: String?,
    expenseSectionTitle: String,
    incomeSectionTitle: String,
    onSelect: (CategoryPickerRow) -> Unit,
    modifier: Modifier = Modifier,
    createNewLabel: String? = null,
    onCreateNewClick: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(categories, query) {
        if (query.isBlank()) {
            categories
        } else {
            categories.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
    }
    val expenseItems = filtered.filterNot { it.isIncome }
    val incomeItems = filtered.filter { it.isIncome }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )
        SurferSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = searchPlaceholder,
            containerColor = AppTheme.materialColors.surfaceContainerHighest,
        )

        if (createNewLabel != null && onCreateNewClick != null) {
            Spacer(Modifier.height(12.dp))
            CreateNewCategoryRow(
                label = createNewLabel,
                onClick = onCreateNewClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 480.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (expenseItems.isNotEmpty()) {
                item(key = "expense-header", span = { GridItemSpan(maxLineSpan) }) {
                    SurferSectionLabel(
                        text = expenseSectionTitle,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(expenseItems, key = { "e-${it.id}" }) { cat ->
                    SurferCategoryCell(
                        name = cat.name,
                        icon = cat.icon,
                        tint = cat.tint,
                        selected = cat.id == selectedId,
                        onClick = { onSelect(cat) },
                    )
                }
            }
            if (incomeItems.isNotEmpty()) {
                item(key = "income-header", span = { GridItemSpan(maxLineSpan) }) {
                    SurferSectionLabel(
                        text = incomeSectionTitle,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(incomeItems, key = { "i-${it.id}" }) { cat ->
                    SurferCategoryCell(
                        name = cat.name,
                        icon = cat.icon,
                        tint = cat.tint,
                        selected = cat.id == selectedId,
                        onClick = { onSelect(cat) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateNewCategoryRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferActionCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.Add,
                    contentDescription = SurferSemantics.Decorative,
                    tint = AppTheme.materialColors.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Composable: category tints are theme-dependent, so the rows resolve inside the preview.
@Composable
private fun previewRows(): List<CategoryPickerRow> = listOf(
    CategoryPickerRow(
        id = "groceries",
        name = "Groceries",
        icon = SurferCategoryPalette.iconFor("groceries"),
        tint = SurferCategoryPalette.tintFor("groceries"),
    ),
    CategoryPickerRow(
        id = "dining",
        name = "Dining",
        icon = SurferCategoryPalette.iconFor("dining"),
        tint = SurferCategoryPalette.tintFor("dining"),
    ),
    CategoryPickerRow(
        id = "transport",
        name = "Transport",
        icon = SurferCategoryPalette.iconFor("transport"),
        tint = SurferCategoryPalette.tintFor("transport"),
    ),
    CategoryPickerRow(
        id = "rent",
        name = "Rent",
        icon = SurferCategoryPalette.iconFor("rent"),
        tint = SurferCategoryPalette.tintFor("rent"),
    ),
    CategoryPickerRow(
        id = "salary",
        name = "Salary",
        icon = SurferCategoryPalette.iconFor("salary"),
        tint = SurferCategoryPalette.tintFor("salary"),
        isIncome = true,
    ),
    CategoryPickerRow(
        id = "freelance",
        name = "Freelance",
        icon = SurferCategoryPalette.iconFor("freelance"),
        tint = SurferCategoryPalette.tintFor("freelance"),
        isIncome = true,
    ),
)

@Preview
@Composable
private fun CategoryChooserContentPreview() {
    SurferBottomSheetPreview {
        CategoryChooserContent(
            title = "Category",
            searchPlaceholder = "Search categories",
            categories = previewRows(),
            selectedId = "groceries",
            expenseSectionTitle = "Expense",
            incomeSectionTitle = "Income",
            onSelect = {},
            createNewLabel = "Create new category",
            onCreateNewClick = {},
        )
    }
}

@Preview
@Composable
private fun CategoryChooserContentEmptyPreview() {
    SurferBottomSheetPreview {
        CategoryChooserContent(
            title = "Category",
            searchPlaceholder = "Search categories",
            categories = emptyList(),
            selectedId = null,
            expenseSectionTitle = "Expense",
            incomeSectionTitle = "Income",
            onSelect = {},
        )
    }
}
