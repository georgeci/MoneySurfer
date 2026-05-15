package com.georgeci.moneysurfer.uikit.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * A single shimmering placeholder block. Sweeps a highlight band across a muted base so a
 * loading screen reads as "content is on the way" rather than an empty void. Compose these
 * into screen-shaped skeletons (see [SurferSkeletonRow]).
 */
@Composable
fun SurferSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = AppTheme.shapes.medium,
) {
    val base = AppTheme.materialColors.onSurface.copy(alpha = 0.08f)
    val highlight = AppTheme.materialColors.onSurface.copy(alpha = 0.16f)

    var widthPx by remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "surfer-skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "surfer-skeleton-progress",
    )

    val travel = widthPx * 2f
    val translate = progress * travel - widthPx
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + widthPx, 0f),
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .background(brush),
    )
}

/**
 * A list-row skeleton: a circular avatar placeholder beside two stacked text-line
 * placeholders. Drop several into a column to skeleton-out a loading list.
 */
@Composable
fun SurferSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SurferSkeleton(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
        )
        Spacer(Modifier.width(AppTheme.spacing.medium))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xSmall),
        ) {
            SurferSkeleton(modifier = Modifier.fillMaxWidth(fraction = 0.55f).height(14.dp))
            SurferSkeleton(modifier = Modifier.fillMaxWidth(fraction = 0.35f).height(12.dp))
        }
        Spacer(Modifier.width(AppTheme.spacing.medium))
        SurferSkeleton(modifier = Modifier.width(64.dp).height(16.dp))
    }
}

@Preview
@Composable
private fun SurferSkeletonPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferSkeleton(modifier = Modifier.fillMaxWidth().height(160.dp))
            SurferSkeletonRow()
            SurferSkeletonRow()
        }
    }
}
