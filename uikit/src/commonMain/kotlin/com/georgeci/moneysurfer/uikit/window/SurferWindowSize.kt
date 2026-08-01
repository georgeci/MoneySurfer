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
 * Height breakpoints are deliberately absent: Material scales layouts by width, and a second set of
 * named bands would double the vocabulary for no screen that wants it. The one thing height *is*
 * needed for — telling a wide-because-landscape window from a wide-because-large one — is the
 * single [SurferWindowGeometry.isLandscape] flag rather than a band of its own.
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
 * Both axes of the window, for the few layouts that cannot decide on width alone.
 *
 * Reach for [sizeClass] by default. [isLandscape] exists because "expanded" answers *how much* room
 * there is but not *what shape* it is: a 1024 dp tablet reports the same width lying down and stood
 * up, and a layout that puts two columns side by side wants the first and not the second.
 */
data class SurferWindowGeometry(val width: Dp, val height: Dp) {

    /** The width band, i.e. what [currentSurferWindowSize] returns. */
    val sizeClass: SurferWindowSize = SurferWindowSize.ofWidth(width)

    /** Whether the window is wider than it is tall. */
    val isLandscape: Boolean = width > height
}

/**
 * The [SurferWindowGeometry] of the window this composition is hosted in, recomposing when the
 * window is resized.
 *
 * Measures `LocalWindowInfo.containerSize` in the current density — the same input
 * `currentWindowAdaptiveInfo()` derives its `WindowSizeClass` from, minus the dependency on the
 * `androidx.window` breakpoint sets (whose default set stops at Expanded and would collapse
 * [SurferWindowSize.Large] into it).
 *
 * Prefer this over measuring a `BoxWithConstraints` at a screen root: the window is what both
 * values are about, reading it costs no subcomposition, and it does not shrink when the keyboard
 * opens — so a layout chosen from it cannot flip while the user is typing.
 */
@Composable
fun currentSurferWindowGeometry(): SurferWindowGeometry {
    val containerSize = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        SurferWindowGeometry(width = containerSize.width.toDp(), height = containerSize.height.toDp())
    }
}

/** The width band of the window this composition is hosted in. */
@Composable
fun currentSurferWindowSize(): SurferWindowSize = currentSurferWindowGeometry().sizeClass
