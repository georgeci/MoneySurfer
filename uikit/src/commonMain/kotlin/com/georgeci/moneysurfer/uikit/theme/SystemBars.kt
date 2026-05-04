package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.Composable

/**
 * Configures the appearance of the platform system bars (status bar + navigation bar) to match the
 * current theme. On Android this toggles light/dark icon tints via `WindowInsetsControllerCompat`;
 * on iOS it sets the UIKit status bar style; on Desktop it is a no-op.
 *
 * Called once from [AppTheme] — consumers should not call it directly.
 */
@Composable
expect fun ConfigureSystemBars(darkTheme: Boolean)
