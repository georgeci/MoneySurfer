package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Donut version of [SurferBudgetProgressBar] — the hero on Budget details. Sweeps clockwise
 * from twelve o'clock, capped at a full turn so an overspent budget shows a closed ring in the
 * error colour rather than lapping itself.
 *
 * [content] is stacked in the hole (percent, spent, "of limit"); the caller owns that text so
 * the ring stays free of formatting concerns.
 */
@Composable
fun SurferBudgetRing(
    progress: Float,
    status: SurferBudgetStatus,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit = {},
) {
    val fill = status.color
    val track = AppTheme.materialColors.surfaceContainerHighest
    val capped = progress.coerceIn(0f, 1f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = FULL_TURN,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (capped > 0f) {
                drawArc(
                    color = fill,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_TURN * capped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Twelve o'clock — Compose measures arc angles from three o'clock. */
private const val START_ANGLE = -90f
private const val FULL_TURN = 360f

@Preview
@Composable
private fun SurferBudgetRingPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(
                0.42f to SurferBudgetStatus.Ok,
                0.86f to SurferBudgetStatus.Warn,
                1.2f to SurferBudgetStatus.Over,
            ).forEach { (progress, status) ->
                SurferBudgetRing(progress = progress, status = status, size = 120.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = AppTheme.typography.headlineSmall,
                            color = AppTheme.materialColors.onSurface,
                        )
                        Text(
                            text = "of €400",
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.materialColors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
