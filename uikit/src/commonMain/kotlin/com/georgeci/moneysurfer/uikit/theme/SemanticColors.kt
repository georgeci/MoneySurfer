package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.georgeci.moneysurfer.uikit.tokens.AppColors

data class SemanticColors(
    val income: Color = AppColors.Income,
)

internal val LightSemanticColors = SemanticColors(
    income = AppColors.Income,
)

internal val DarkSemanticColors = SemanticColors(
    income = AppColors.Dark.Income,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
