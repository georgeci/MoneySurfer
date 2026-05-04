package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

enum class SurferWidgetSize { Hero, Compact }

val LocalSurferWidgetSize: ProvidableCompositionLocal<SurferWidgetSize> =
    staticCompositionLocalOf { SurferWidgetSize.Hero }
