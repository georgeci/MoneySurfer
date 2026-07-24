package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.Composable

/**
 * Configures the appearance of the platform system bars (status bar + navigation bar) to match the
 * surface drawn underneath them. On Android this toggles light/dark icon tints via
 * `WindowInsetsControllerCompat` and restores the previous tints when the caller leaves the
 * composition; on iOS the status bar style is owned by the host view controller; on Desktop it is
 * a no-op.
 *
 * Pass `true` for a bar whose backdrop is dark, so its icons are drawn light. The two bars are
 * configured separately because a screen can be light at the top and dark at the bottom, or the
 * other way round. Screens with a fixed-colour backdrop (the pre-auth screens, for instance) call
 * this with constants rather than with the theme flag.
 */
@Composable
expect fun ConfigureSystemBars(
    darkStatusBarBackground: Boolean,
    darkNavigationBarBackground: Boolean = darkStatusBarBackground,
)
