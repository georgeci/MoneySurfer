package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

expect val isDynamicColorAvailable: Boolean

@Composable
expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme
