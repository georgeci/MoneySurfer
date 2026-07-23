package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview

/** One selectable category in [SurferCategoryChipGrid]. */
data class SurferCategoryChip(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val tint: Color,
)

/**
 * Multi-select category picker for a budget, with a leading "All categories" chip.
 *
 * "All categories" is not one more selectable item — it is the empty selection, which the
 * domain reads as a general budget. Tapping it clears everything; picking any category clears
 * it. That keeps the two states mutually exclusive in the UI, the way they are in the model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurferCategoryChipGrid(
    categories: List<SurferCategoryChip>,
    selectedIds: Set<String>,
    allCategoriesLabel: String,
    onToggle: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedIds.isEmpty(),
            onClick = onSelectAllCategories,
            label = { Text(allCategoriesLabel) },
            leadingIcon = {
                Icon(
                    imageVector = SurferIcons.Category,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
        categories.forEach { category ->
            FilterChip(
                selected = category.id in selectedIds,
                onClick = { onToggle(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = category.tint,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Preview
@Composable
private fun SurferCategoryChipGridPreview() {
    SurferComponentPreview {
        var selected by remember { mutableStateOf(setOf("groceries")) }
        val categories = listOf(
            SurferCategoryChip("groceries", "Groceries", SurferIcons.Receipt, SurferCategoryPalette.tints[0]),
            SurferCategoryChip("dining", "Eating out", SurferIcons.Cash, SurferCategoryPalette.tints[1]),
            SurferCategoryChip("transport", "Transport", SurferIcons.SwapHoriz, SurferCategoryPalette.tints[2]),
            SurferCategoryChip("leisure", "Leisure", SurferIcons.Event, SurferCategoryPalette.tints[3]),
        )
        SurferCategoryChipGrid(
            modifier = Modifier.padding(16.dp),
            categories = categories,
            selectedIds = selected,
            allCategoriesLabel = "All categories",
            onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
            onSelectAllCategories = { selected = emptySet() },
        )
    }
}
