package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Grid cell for the category picker / creation screens. Renders a [SurferCategoryBubble] over
 * a centered label. Selection promotes the bubble's surrounding ring to the primary color and
 * tints the label with the secondaryContainer palette so the cell reads as the active choice.
 */
@Composable
fun SurferCategoryCell(
    name: String,
    icon: ImageVector,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent
    val labelColor = if (selected) {
        AppTheme.materialColors.onSecondaryContainer
    } else {
        AppTheme.materialColors.onSurface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) AppTheme.materialColors.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            SurferCategoryBubble(icon = icon, tint = tint, size = 44.dp)
        }
        Text(
            text = name,
            style = AppTheme.typography.labelMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Preview
@Composable
private fun SurferCategoryCellPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferCategoryCell(
                name = "Salary",
                icon = SurferIcons.Cash,
                tint = SurferCategoryPalette.tints[1],
                selected = false,
                onClick = {},
                modifier = Modifier.size(width = 80.dp, height = 96.dp),
            )
            SurferCategoryCell(
                name = "Salary",
                icon = SurferIcons.Cash,
                tint = SurferCategoryPalette.tints[1],
                selected = true,
                onClick = {},
                modifier = Modifier.size(width = 80.dp, height = 96.dp),
            )
        }
    }
}
