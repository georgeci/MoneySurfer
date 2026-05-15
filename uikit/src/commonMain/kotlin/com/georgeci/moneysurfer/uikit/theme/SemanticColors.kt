package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.georgeci.moneysurfer.uikit.tokens.AppColors

data class SemanticColors(
    val income: Color = AppColors.Income,
    val expense: Color = AppColors.Expense,
    val transfer: Color = AppColors.Transfer,
)

internal val LightSemanticColors = SemanticColors(
    income = AppColors.Income,
    expense = AppColors.Expense,
    transfer = AppColors.Transfer,
)

internal val DarkSemanticColors = SemanticColors(
    income = AppColors.Dark.Income,
    expense = AppColors.Dark.Expense,
    transfer = AppColors.Dark.Transfer,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
