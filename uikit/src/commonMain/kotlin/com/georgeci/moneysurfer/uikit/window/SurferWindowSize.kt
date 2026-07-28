package com.georgeci.moneysurfer.uikit.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Material 3 window *width* breakpoints, named once for the whole app.
 *
 * Before this existed every caller reached for `androidx.window.core.layout` constants directly,
 * so "wide" meant whatever each screen happened to compare against. Features depend on this enum
 * instead; `androidx.window` stays an implementation detail of the windowing libraries.
 *
 * Entries are declared narrow-to-wide, so the natural enum ordering is also the width ordering:
 * `currentSurferWindowSize() >= SurferWindowSize.Medium` reads as "at least Medium".
 *
 * Height breakpoints are deliberately absent — nothing in the app branches on window height yet,
 * and an unused axis is one more thing to keep honest.
 */
enum class SurferWindowSize {
    /** `< 600 dp` — phones in portrait, and the narrow pane of a split-screen window. */
    Compact,

    /** `600–839 dp` — large phones in landscape, small tablets, resized desktop windows. */
    Medium,

    /** `840–1199 dp` — tablets in landscape, most desktop windows. */
    Expanded,

    /** `>= 1200 dp` — maximised desktop windows and large external displays. */
    Large,

    ;

    companion object {
        /** Lower bound of [Medium]. */
        val MediumLowerBound: Dp = 600.dp

        /** Lower bound of [Expanded]. */
        val ExpandedLowerBound: Dp = 840.dp

        /** Lower bound of [Large]. */
        val LargeLowerBound: Dp = 1200.dp

        /** Buckets a window [width] into a breakpoint. Widest bound first — the order matters. */
        fun ofWidth(width: Dp): SurferWindowSize = when {
            width >= LargeLowerBound -> Large
            width >= ExpandedLowerBound -> Expanded
            width >= MediumLowerBound -> Medium
            else -> Compact
        }
    }
}

/**
 * The [SurferWindowSize] of the window this composition is hosted in, recomposing when the window
 * is resized.
 *
 * Measures `LocalWindowInfo.containerSize` in the current density — the same input
 * `currentWindowAdaptiveInfo()` derives its `WindowSizeClass` from, minus the dependency on the
 * `androidx.window` breakpoint sets (whose default set stops at Expanded and would collapse
 * [SurferWindowSize.Large] into it).
 */
@Composable
fun currentSurferWindowSize(): SurferWindowSize {
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { containerWidth.toDp() }
    return SurferWindowSize.ofWidth(widthDp)
}
