package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.Composable

@Composable
actual fun ConfigureSystemBars(darkTheme: Boolean) {
    // Status bar style on iOS is owned by the host UIViewController. Wire
    // `preferredStatusBarStyle` in `MainViewController` to `.lightContent` or
    // `.darkContent` based on the current theme when needed. Kept as a no-op so
    // commonMain can call it unconditionally.
}
