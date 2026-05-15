package com.georgeci.moneysurfer.uikit.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Animated shimmer fill applied as a background. Use it to mark the bounds of content that is
 * still loading — pair with fixed sizes so the skeleton matches the eventual layout. Prefer
 * [SurferShimmerBox] for a ready-made clipped block; reach for this modifier when you need the
 * shimmer on a custom shape.
 *
 * Once data arrives, swap the shimmer node for the real content. For a successful-but-empty
 * result use [SurferEmptyState]; for a failure use [SurferErrorState].
 */
@Composable
fun Modifier.surferShimmer(shape: Shape = AppTheme.shapes.small): Modifier {
    val transition = rememberInfiniteTransition(label = "surferShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "surferShimmerProgress",
    )

    val base = AppTheme.materialColors.surfaceVariant
    val highlight = AppTheme.materialColors.surface

    // Scale the travel to the measured width so the highlight band crosses the whole node
    // regardless of how wide it lays out (skeletons routinely use fillMaxWidth).
    var width by remember { mutableStateOf(0f) }
    val span = width.coerceAtLeast(1f)
    // Travel the gradient from fully off the left edge to fully off the right edge.
    val translate = progress * 2f * span - span
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + span, 0f),
    )

    return this
        .clip(shape)
        .onSizeChanged { width = it.width.toFloat() }
        .background(brush)
}

/**
 * Ready-made shimmer skeleton block: a clipped, sized [androidx.compose.foundation.layout.Box]
 * filled with [surferShimmer]. Set [height] (and a width via [modifier]) to mirror the real
 * element it stands in for.
 */
@Composable
fun SurferShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = AppTheme.shapes.small,
) {
    Box(
        modifier = modifier
            .height(height)
            .surferShimmer(shape),
    )
}

@Preview
@Composable
private fun SurferShimmerBoxPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            SurferShimmerBox(modifier = Modifier.size(56.dp), shape = AppTheme.shapes.large)
            SurferShimmerBox(modifier = Modifier.fillMaxWidth(), height = 20.dp)
            SurferShimmerBox(modifier = Modifier.fillMaxWidth(0.6f), height = 16.dp)
            SurferShimmerBox(modifier = Modifier.fillMaxWidth(0.8f), height = 16.dp)
        }
    }
}
