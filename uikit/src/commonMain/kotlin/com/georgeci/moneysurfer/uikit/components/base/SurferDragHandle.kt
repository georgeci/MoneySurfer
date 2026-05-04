package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Six-dot reorder grip rendered with [drawBehind] so it doesn't pull a vector dependency. Sized
 * to the standard 24x40 list-affordance hit target; tint defaults to onSurfaceVariant.
 */
@Composable
fun SurferDragHandle(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.materialColors.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(width = 24.dp, height = 40.dp)
            .drawBehind {
                val dotRadius = 1.4.dp.toPx()
                val stepX = 6.dp.toPx()
                val stepY = 6.dp.toPx()
                val originX = size.width / 2f - stepX / 2f
                val originY = size.height / 2f - stepY
                for (row in 0..2) {
                    for (col in 0..1) {
                        drawCircle(
                            color = color,
                            radius = dotRadius,
                            center = Offset(originX + col * stepX, originY + row * stepY),
                        )
                    }
                }
            },
    )
}

@Preview
@Composable
private fun SurferDragHandlePreview() {
    SurferComponentPreview {
        SurferDragHandle()
    }
}
