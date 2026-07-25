package com.georgeci.moneysurfer.uikit.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Lays the content out at [contentWidth] and then draws it shrunk to the width the parent offers,
 * reporting the scaled-down size so the surrounding layout still measures correctly.
 *
 * This is what makes a thumbnail of a real screen component possible: a widget rendered directly
 * into a 150dp tile is not the same widget the dashboard shows — its text wraps, its rows collapse,
 * its hero typography overflows. Measuring at the dashboard's own width and scaling the result
 * keeps the proportions the user is choosing between.
 *
 * Scale is capped at 1: a tile wider than [contentWidth] shows the component at its natural size
 * rather than magnifying it into a blur.
 */
fun Modifier.surferScaleToWidth(contentWidth: Dp): Modifier = this.layout { measurable, constraints ->
    val target = contentWidth.roundToPx().coerceAtLeast(1)
    val placeable = measurable.measure(
        Constraints(
            minWidth = target,
            maxWidth = target,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        ),
    )
    val available = if (constraints.hasBoundedWidth) constraints.maxWidth else target
    val scale = (available.toFloat() / target).coerceAtMost(1f)
    layout((target * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(x = 0, y = 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
