package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class SurferContainerStyle {
    Filled,
    Outlined,
    Card,
}

internal val LocalContainerStyle = staticCompositionLocalOf { SurferContainerStyle.Card }
