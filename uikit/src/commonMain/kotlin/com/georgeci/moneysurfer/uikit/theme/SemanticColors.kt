package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.georgeci.moneysurfer.uikit.tokens.AppColors

@Immutable
data class SemanticColors(
    val income: Color = AppColors.Income,
    val expense: Color = AppColors.Expense,
    val transfer: Color = AppColors.Transfer,
    /** Caution accent — a budget past its alert threshold but not yet over. */
    val warning: Color = AppColors.Warning,
    /**
     * Category tints in palette order. Read through `SurferCategoryPalette`, which owns the
     * hue/icon-key mapping onto this list; call sites go through its resolvers.
     */
    val categoryTints: List<Color> = AppColors.CategoryTints,
)

internal val LightSemanticColors = SemanticColors(
    income = AppColors.Income,
    expense = AppColors.Expense,
    transfer = AppColors.Transfer,
    warning = AppColors.Warning,
    categoryTints = AppColors.CategoryTints,
)

internal val DarkSemanticColors = SemanticColors(
    income = AppColors.Dark.Income,
    expense = AppColors.Dark.Expense,
    transfer = AppColors.Dark.Transfer,
    warning = AppColors.Dark.Warning,
    categoryTints = AppColors.Dark.CategoryTints,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
