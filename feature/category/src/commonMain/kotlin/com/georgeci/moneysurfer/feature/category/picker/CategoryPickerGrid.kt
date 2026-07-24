package com.georgeci.moneysurfer.feature.category.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryCell
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferDashedBorder
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_picker_new
import org.jetbrains.compose.resources.stringResource

/** Column count the artboard specifies for the flat grid. */
private const val GridColumns = 4

private val GridMinHeight = 200.dp
private val GridMaxHeight = 420.dp
private val GridSpacing = 10.dp
private val BubbleSize = 44.dp
private val CellRadius = 14.dp
private val NewGlyphSize = 20.dp
private val CellVerticalPadding = 8.dp
private val CellLabelSpacing = 6.dp

/**
 * Variant A of the picker: a flat 4-column grid of every category of the selected type, parents
 * and children mixed with no hierarchy — the fast path for someone who already knows the name.
 * The last cell always creates a new category.
 */
@Composable
internal fun CategoryPickerGrid(
    items: List<CategoryPickerItem>,
    onSelect: (CategoryId) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GridColumns),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GridMinHeight, max = GridMaxHeight),
        contentPadding = PaddingValues(
            horizontal = SheetBodyPadding,
            vertical = CellVerticalPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
    ) {
        items(items, key = { it.id.value }) { item ->
            val visual = SurferCategoryPalette.visualFor(
                id = item.id.value,
                iconKey = item.iconKey,
                hue = item.hue,
                systemKind = item.systemKind,
            )
            SurferCategoryCell(
                name = item.name,
                icon = visual.icon,
                tint = visual.tint,
                selected = item.selected,
                onClick = { onSelect(item.id) },
            )
        }
        item(key = "create-new") {
            NewCategoryCell(onClick = onCreateNew)
        }
    }
}

/** The trailing "New" tile — a dashed outline circle, so it never reads as a real category. */
@Composable
private fun NewCategoryCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = AppTheme.materialColors.outline
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CellRadius))
            .clickable(onClick = onClick)
            .padding(vertical = CellVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CellLabelSpacing),
    ) {
        Box(
            modifier = Modifier
                .size(BubbleSize)
                .surferDashedBorder(color = outline, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Add,
                // decorative — the label under the tile names the action
                contentDescription = null,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(NewGlyphSize),
            )
        }
        Text(
            text = stringResource(Res.string.category_picker_new),
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
