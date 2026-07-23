package com.georgeci.moneysurfer.uikit.modifier

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a dashed outline inset by half the stroke so the whole line stays inside the bounds.
 * Shared by the dashed-card chrome and its satellite decorations (e.g. the "+" bubble) so a
 * single dash rhythm is used everywhere — do not hand-roll another `dashPathEffect`.
 */
fun Modifier.surferDashedBorder(
    color: Color,
    shape: Shape,
    strokeWidth: Dp = 1.5.dp,
    dashLength: Dp = 6.dp,
    dashGap: Dp = 4.dp,
): Modifier = this.drawBehind {
    val stroke = strokeWidth.toPx()
    val inset = stroke / 2f
    val cornerRadius = when (shape) {
        is RoundedCornerShape -> shape.topStart.toPx(Size(size.width, size.height), this)
        else -> 0f
    }
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius - inset, cornerRadius - inset),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), dashGap.toPx()), 0f),
        ),
    )
}
