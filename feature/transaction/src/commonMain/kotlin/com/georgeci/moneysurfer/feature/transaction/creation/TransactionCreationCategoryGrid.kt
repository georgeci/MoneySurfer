package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_all
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_more
import org.jetbrains.compose.resources.stringResource

private const val CATEGORY_PREVIEW_SIZE = 7

/** Columns in the tile grid; the last cell of the last row is always "More". */
private const val CATEGORY_GRID_COLUMNS = 4

@Composable
internal fun CategoryGridSection(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit,
    onAllClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.transaction_creation_category_label),
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.transaction_creation_category_all),
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.clickable(onClick = onAllClick),
            )
        }
        Spacer(Modifier.height(8.dp))
        val preview = categories.take(CATEGORY_PREVIEW_SIZE)
        val rows = (preview + listOf<Category?>(null)).chunked(CATEGORY_GRID_COLUMNS)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cat ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (cat != null) {
                                CategoryTile(
                                    category = cat,
                                    selected = cat.id == selected?.id,
                                    onClick = { onSelect(cat) },
                                )
                            } else {
                                MoreTile(onClick = onMoreClick)
                            }
                        }
                    }
                    // Pad row if last row is shorter than the grid.
                    if (row.size < CATEGORY_GRID_COLUMNS) {
                        repeat(CATEGORY_GRID_COLUMNS - row.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent
    val labelColor =
        if (selected) AppTheme.materialColors.onSecondaryContainer else AppTheme.materialColors.onSurface
    val visual = SurferCategoryPalette.visualFor(
        id = category.id.value,
        iconKey = category.iconKey,
        hue = category.hue,
        systemKind = category.systemKind?.name,
    )
    val tint = visual.tint
    val icon: ImageVector = visual.icon
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SurferCategoryBubble(icon = icon, tint = tint, size = 44.dp)
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AppTheme.materialColors.primary)
                        .border(2.dp, AppTheme.materialColors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SurferIcons.Check,
                        contentDescription = SurferSemantics.Decorative,
                        tint = AppTheme.materialColors.onPrimary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Text(
            text = category.name,
            style = AppTheme.typography.labelMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun MoreTile(onClick: () -> Unit) {
    val outline = AppTheme.materialColors.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.5.dp, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Add,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(Res.string.transaction_creation_category_more),
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.primary,
            textAlign = TextAlign.Center,
        )
    }
}
