package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
 *
 * [color] defaults to the colour [status] maps to, which is what a budget screen wants. A caller
 * whose shape is not primarily about a budget overrides it — the dashboard's spent-by-category card
 * paints a category in its own tint until its cap has something to report, and passes that decision
 * here so its ring cannot disagree with the legend beside it.
 */
@Composable
fun SurferBudgetRing(
    progress: Float,
    status: SurferBudgetStatus,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    strokeWidth: Dp = 14.dp,
    color: Color = status.color,
    content: @Composable () -> Unit = {},
) {
    ArcMeter(
        progress = progress,
        color = color,
        span = ArcSpan.FullTurn,
        modifier = modifier,
        width = size,
        strokeWidth = strokeWidth,
        content = content,
    )
}

/**
 * Half-turn sibling of [SurferBudgetRing] — the dial the dashboard's spent-by-category card draws
 * for a single category. Sweeps clockwise from nine o'clock through twelve to three, so [content]
 * sits under the arc rather than inside a hole, and the card only pays for the height the dial
 * actually uses.
 *
 * Shares [ArcMeter] with the ring on purpose: the two differ in the arc they span and nothing else,
 * and a second copy of the geometry is how a status colour or a cap ends up fixed in one shape and
 * not the other. [color] overrides the status colour exactly as it does on [SurferBudgetRing].
 */
@Composable
fun SurferBudgetGauge(
    progress: Float,
    status: SurferBudgetStatus,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
    strokeWidth: Dp = 12.dp,
    color: Color = status.color,
    content: @Composable () -> Unit = {},
) {
    ArcMeter(
        progress = progress,
        color = color,
        span = ArcSpan.TopHalf,
        modifier = modifier,
        width = width,
        strokeWidth = strokeWidth,
        content = content,
    )
}

/**
 * How much of the circle a meter draws, and what that leaves for its content.
 *
 * [heightRatio] is the share of the meter's width the box keeps: a full turn needs a square, a top
 * half only needs enough for the arc plus the line under it.
 */
private data class ArcSpan(
    val startDegrees: Float,
    val sweepDegrees: Float,
    val heightRatio: Float,
    val contentAlignment: Alignment,
) {
    companion object {
        /** Twelve o'clock all the way round — Compose measures arc angles from three o'clock. */
        val FullTurn = ArcSpan(
            startDegrees = -90f,
            sweepDegrees = 360f,
            heightRatio = 1f,
            contentAlignment = Alignment.Center,
        )

        /** Nine o'clock to three o'clock over the top. */
        val TopHalf = ArcSpan(
            startDegrees = 180f,
            sweepDegrees = 180f,
            heightRatio = 0.62f,
            contentAlignment = Alignment.BottomCenter,
        )
    }
}

@Composable
private fun ArcMeter(
    progress: Float,
    color: Color,
    span: ArcSpan,
    modifier: Modifier,
    width: Dp,
    strokeWidth: Dp,
    content: @Composable () -> Unit,
) {
    val track = AppTheme.materialColors.surfaceContainerHighest
    val capped = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .width(width)
            .height(width * span.heightRatio),
        contentAlignment = span.contentAlignment,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The arc always lives in a square the width of the box, anchored at its top — a
            // partial span simply leaves the rest of that square outside the box, unpainted.
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.width - stroke)
            drawArc(
                color = track,
                startAngle = span.startDegrees,
                sweepAngle = span.sweepDegrees,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (capped > 0f) {
                drawArc(
                    color = color,
                    startAngle = span.startDegrees,
                    sweepAngle = span.sweepDegrees * capped,
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

private val previewSamples = listOf(
    0.42f to SurferBudgetStatus.Ok,
    0.86f to SurferBudgetStatus.Warn,
    1.2f to SurferBudgetStatus.Over,
)

@Preview
@Composable
private fun SurferBudgetRingPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            previewSamples.forEach { (progress, status) ->
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

@Preview
@Composable
private fun SurferBudgetGaugePreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            previewSamples.forEach { (progress, status) ->
                SurferBudgetGauge(progress = progress, status = status, width = 120.dp) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.materialColors.onSurface,
                    )
                }
            }
        }
    }
}
