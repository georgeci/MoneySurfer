package com.georgeci.moneysurfer.uikit.components.goal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Flat progress bar for a goal card. v1 draws the fill only — the mockup's "expected today"
 * tick ships with the forecast work (md/goals.md).
 */
@Composable
fun SurferGoalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.materialColors.primary,
    height: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(AppTheme.materialColors.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Details-screen hero: a progress ring with `% → saved → of target` stacked inside it.
 *
 * The arc is shared with the dashboard [com.georgeci.moneysurfer.uikit.widgets.SurferGoalsWidget]
 * via [SurferGoalRingArc] so the two never drift apart.
 */
@Composable
fun SurferGoalProgressRing(
    progress: Float,
    percentLabel: String,
    savedLabel: String,
    targetLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    color: Color = AppTheme.materialColors.primary,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        SurferGoalRingArc(progress = progress, size = size, color = color)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = percentLabel,
                style = AppTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = savedLabel,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = targetLabel,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/** The bare ring: track plus a rounded fill arc starting at twelve o'clock. */
@Composable
fun SurferGoalRingArc(
    progress: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.materialColors.primary,
) {
    val track = AppTheme.materialColors.surfaceContainerHigh
    Canvas(modifier = modifier.size(size)) {
        val stroke = (this.size.minDimension / RING_STROKE_DIVISOR).coerceAtLeast(MIN_STROKE)
        val inset = stroke / 2f
        val box = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = FULL_TURN,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke),
        )
        rotate(degrees = QUARTER_TURN_BACK) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = FULL_TURN * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private const val RING_STROKE_DIVISOR = 16f
private const val MIN_STROKE = 3f
private const val FULL_TURN = 360f
private const val QUARTER_TURN_BACK = -90f

@Preview
@Composable
private fun SurferGoalProgressPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferGoalProgressBar(progress = 0.62f)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SurferGoalProgressRing(
                    progress = 0.74f,
                    percentLabel = "74%",
                    savedLabel = "€8,915",
                    targetLabel = "of €12,000",
                )
            }
        }
    }
}
