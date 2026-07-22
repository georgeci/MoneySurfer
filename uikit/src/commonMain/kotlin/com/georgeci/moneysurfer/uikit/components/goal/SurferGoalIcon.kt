package com.georgeci.moneysurfer.uikit.components.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Emoji tile for a savings goal: a squircle filled with a tint derived from the goal's hue.
 *
 * Goals are the design system's second sanctioned use of emoji (workspace icons are the
 * first) — see decision 8 in md/goals.md.
 */
@Composable
fun SurferGoalIcon(
    emoji: String,
    hue: Int,
    modifier: Modifier = Modifier,
    size: Dp = SurferGoalIconDefaults.Size,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(SurferGoalIconDefaults.CORNER_PERCENT))
            .background(goalTileColor(hue)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = (size.value * SurferGoalIconDefaults.EMOJI_RATIO).sp),
        )
    }
}

object SurferGoalIconDefaults {
    val Size: Dp = 44.dp

    /** The mockup draws the tile between a 32% and 38% corner radius; 35% splits the two. */
    const val CORNER_PERCENT: Int = 35
    const val EMOJI_RATIO: Float = 0.5f
}

/**
 * Tile fill for a goal hue. Saturation and lightness are fixed per theme so a hue picked in
 * light mode stays legible in dark mode instead of glowing.
 */
@Composable
@ReadOnlyComposable
fun goalTileColor(hue: Int): Color {
    val dark = isDarkSurface()
    return Color.hsl(
        hue = hue.coerceIn(0, HUE_MAX).toFloat(),
        saturation = if (dark) 0.35f else 0.55f,
        lightness = if (dark) 0.28f else 0.90f,
    )
}

/** Accent for a goal hue — progress fills, rings and the emoji-picker selection ring. */
@Composable
@ReadOnlyComposable
fun goalAccentColor(hue: Int): Color {
    val dark = isDarkSurface()
    return Color.hsl(
        hue = hue.coerceIn(0, HUE_MAX).toFloat(),
        saturation = if (dark) 0.55f else 0.65f,
        lightness = if (dark) 0.62f else 0.45f,
    )
}

private const val HUE_MAX = 360
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * The theme carries no `isDark` flag, and hue tints need one: the same hue has to be pale on a
 * light surface and muted on a dark one. Reading the surface luminance keeps that decision here
 * instead of threading a flag through every goal composable.
 */
@Composable
@ReadOnlyComposable
private fun isDarkSurface(): Boolean =
    AppTheme.materialColors.surface.luminance() < DARK_SURFACE_LUMINANCE

@Preview
@Composable
private fun SurferGoalIconPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferGoalIcon(emoji = "🚲", hue = 210)
            SurferGoalIcon(emoji = "🏖️", hue = 30)
            SurferGoalIcon(emoji = "🏠", hue = 140, size = 56.dp)
        }
    }
}
