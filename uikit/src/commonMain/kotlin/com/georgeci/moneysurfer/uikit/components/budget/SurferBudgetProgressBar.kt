package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Budget spend as a bar, with an optional reference tick the fill is read against.
 *
 * What the tick *means* is the caller's to decide, which is why it is not named after any one
 * reading: the budget cards put it at the alert threshold, the dashboard's safe-to-spend card puts
 * it at how far into the period we are so the bar reads as a pace. Anything that styles or narrates
 * the tick has to keep working for both.
 *
 * The fill is capped at the full width — past 100 % the colour carries the overspend, not the
 * geometry, so an overshoot never draws outside the track. [contentDescription] is what screen
 * readers get: the bar is a picture of numbers stated in the surrounding text, so the caller
 * passes the sentence rather than letting the shape speak for itself.
 */
@Composable
fun SurferBudgetProgressBar(
    progress: Float,
    status: SurferBudgetStatus,
    modifier: Modifier = Modifier,
    tickFraction: Float? = null,
    height: Dp = 8.dp,
    contentDescription: String? = null,
) {
    val fill = status.color
    val track = AppTheme.materialColors.surfaceContainerHighest
    val tickColor = AppTheme.materialColors.onSurfaceVariant
    val capped = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        if (capped > 0f) {
            drawRoundRect(
                color = fill,
                size = Size(size.width * capped, size.height),
                cornerRadius = radius,
            )
        }
        val tick = tickFraction?.coerceIn(0f, 1f) ?: return@Canvas
        val x = (size.width * tick).coerceIn(0f, size.width - TICK_WIDTH_PX)
        drawRect(
            color = tickColor.copy(alpha = 0.6f),
            topLeft = Offset(x, 0f),
            size = Size(TICK_WIDTH_PX, size.height),
        )
    }
}

private const val TICK_WIDTH_PX = 2f

@Preview
@Composable
private fun SurferBudgetProgressBarPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferBudgetProgressBar(progress = 0.35f, status = SurferBudgetStatus.Ok, tickFraction = 0.8f)
            SurferBudgetProgressBar(progress = 0.86f, status = SurferBudgetStatus.Warn, tickFraction = 0.8f)
            SurferBudgetProgressBar(progress = 1.4f, status = SurferBudgetStatus.Over, tickFraction = 0.8f)
            SurferBudgetProgressBar(progress = 0f, status = SurferBudgetStatus.Ok)
        }
    }
}
