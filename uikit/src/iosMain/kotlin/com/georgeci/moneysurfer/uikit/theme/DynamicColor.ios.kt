package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual val isDynamicColorAvailable: Boolean = false

@Composable
actual fun dynamicColorScheme(darkTheme: Boolean): ColorScheme {
    // Dynamic colors are not available on iOS, fall back to static scheme
    return if (darkTheme) DarkColorScheme else LightColorScheme
}
