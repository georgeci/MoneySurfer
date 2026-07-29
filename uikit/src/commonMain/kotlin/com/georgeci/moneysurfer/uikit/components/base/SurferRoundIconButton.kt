package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Round icon button on a tinted disc — the add/remove control for rows that join or leave a list.
 *
 * A switch would be the other option and is the wrong one here: a toggle says "this row has a
 * state", while these rows *move* between two sections when tapped. The disc keeps the tap target
 * at [IconButton]'s 48dp while drawing at [DiscSize].
 */
@Composable
fun SurferRoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.materialColors.surfaceContainerHighest,
    contentColor: Color = AppTheme.materialColors.onSurface,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
    ) {
        Box(
            modifier = Modifier
                .size(DiscSize)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

private val DiscSize: Dp = 32.dp
private val IconSize: Dp = 20.dp

@Preview
@Composable
private fun SurferRoundIconButtonPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurferRoundIconButton(
                icon = SurferIcons.Add,
                contentDescription = "Add",
                onClick = {},
                containerColor = AppTheme.materialColors.primaryContainer,
                contentColor = AppTheme.materialColors.onPrimaryContainer,
            )
            SurferRoundIconButton(
                icon = SurferIcons.Remove,
                contentDescription = "Remove",
                onClick = {},
            )
        }
    }
}
