package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Small outlined action chip: a leading glyph and a label inside a 32dp pill.
 *
 * Lighter than a button and not a filter — it is the "one more thing you may add" affordance,
 * such as the optional account fields or a workspace row's inline action.
 */
@Composable
fun SurferOutlinedChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(ChipHeight)
            .clip(RoundedCornerShape(ChipCorner))
            .border(1.dp, AppTheme.materialColors.outline, RoundedCornerShape(ChipCorner))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.onSurface,
            modifier = Modifier.size(IconSize),
        )
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

private val ChipHeight = 32.dp
private val ChipCorner = 16.dp
private val IconSize = 14.dp

@Preview
@Composable
private fun SurferOutlinedChipPreview() {
    SurferComponentPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
            SurferOutlinedChip(label = "IBAN", icon = SurferIcons.Add, onClick = {})
            SurferOutlinedChip(label = "Description", icon = SurferIcons.Add, onClick = {})
        }
    }
}
