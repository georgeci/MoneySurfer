package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.Composable

@Composable
actual fun ConfigureSystemBars(
    darkStatusBarBackground: Boolean,
    darkNavigationBarBackground: Boolean,
) {
    // Status bar style on iOS is owned by the host view controller. Wire
    // `preferredStatusBarStyle` in `MainViewController` to `.lightContent` or
    // `.darkContent` based on the current screen when needed. Kept as a no-op so
    // commonMain can call it unconditionally.
}
