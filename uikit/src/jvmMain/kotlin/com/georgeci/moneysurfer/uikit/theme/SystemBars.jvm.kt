package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.Composable

// The parameters are part of the `expect` signature; this platform has nothing to apply them
// to, so they stay unused by design.
@Suppress("UNUSED_PARAMETER")
@Composable
actual fun ConfigureSystemBars(
    darkStatusBarBackground: Boolean,
    darkNavigationBarBackground: Boolean,
) {
    // Desktop windows have no system bars.
}
