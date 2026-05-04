package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import com.georgeci.moneysurfer.uikit.tokens.AppSpacing

data class Spacing(
    val none: Dp = AppSpacing.None,
    val xxSmall: Dp = AppSpacing.XXSmall,
    val xSmall: Dp = AppSpacing.XSmall,
    val small: Dp = AppSpacing.Small,
    val medium: Dp = AppSpacing.Medium,
    val default: Dp = AppSpacing.Default,
    val large: Dp = AppSpacing.Large,
    val xLarge: Dp = AppSpacing.XLarge,
    val xxLarge: Dp = AppSpacing.XXLarge,
    val xxxLarge: Dp = AppSpacing.XXXLarge,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
