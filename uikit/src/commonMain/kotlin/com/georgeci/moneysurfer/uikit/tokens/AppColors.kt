package com.georgeci.moneysurfer.uikit.tokens

import androidx.compose.ui.graphics.Color

object AppColors {

    // Primary
    val Primary = Color(0xFF1B5E20)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFA5D6A7)
    val OnPrimaryContainer = Color(0xFF002204)

    // Secondary
    val Secondary = Color(0xFF4E6352)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD0E8D2)
    val OnSecondaryContainer = Color(0xFF0B1F12)

    // Tertiary
    val Tertiary = Color(0xFF39656B)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFBCEBF1)
    val OnTertiaryContainer = Color(0xFF001F23)

    // Error
    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)

    // Background & Surface
    val Background = Color(0xFFFCFDF7)
    val OnBackground = Color(0xFF1A1C19)
    val Surface = Color(0xFFFCFDF7)
    val OnSurface = Color(0xFF1A1C19)
    val SurfaceVariant = Color(0xFFDEE5D9)
    val OnSurfaceVariant = Color(0xFF424940)
    val SurfaceTint = Color(0xFF1B5E20)

    // Outline
    val Outline = Color(0xFF72796F)
    val OutlineVariant = Color(0xFFC2C9BD)

    // Inverse
    val InverseSurface = Color(0xFF2F312D)
    val InverseOnSurface = Color(0xFFF0F1EB)
    val InversePrimary = Color(0xFF6EDD72)

    // Scrim
    val Scrim = Color(0xFF000000)

    // Semantic — transaction type accents. Material 3 has no income/expense/transfer
    // slots; expose via [SemanticColors] / `AppTheme.semanticColors` so screens never
    // reach for the hex.
    val Income = Color(0xFF2E9A6A)
    val Expense = Color(0xFFB54744)
    val Transfer = Color(0xFF2E5AA8)

    /**
     * Amber "approaching the limit" accent. M3 has no warning slot, and `error` is already
     * spoken for by the over-budget state, so a budget nearing its cap needs a third colour
     * that reads as caution rather than failure. Mirrors the mockup's `oklch(56% 0.13 68)`.
     */
    val Warning = Color(0xFFA97110)

    /**
     * Category tints, in palette order (mint, amber, indigo, citrus, plum, rose, clay, moss).
     * Chosen against the light surface; [Dark.CategoryTints] lifts the same hues for the dark
     * one. Consumed through `SemanticColors.categoryTints` / `SurferCategoryPalette.tints` —
     * screens and components never index into this list directly.
     *
     * Positionally paired with `SurferCategoryPalette.hues`; both must stay in lockstep with
     * `CategoryAppearance` in the domain module.
     */
    val CategoryTints: List<Color> = listOf(
        Color(0xFF2F8E6E), // mint   — hue 162
        Color(0xFFB5533B), // amber  — hue  35
        Color(0xFF2E5AA8), // indigo — hue 258
        Color(0xFFA06A1E), // citrus — hue  68
        Color(0xFF6B4E99), // plum   — hue 303
        Color(0xFFB54A6B), // rose   — hue 340
        Color(0xFFB54744), // clay   — hue   8
        Color(0xFF6B7D3D), // moss   — hue  90
    )

    // Dark theme overrides
    object Dark {
        val Primary = Color(0xFF6EDD72)
        val OnPrimary = Color(0xFF00390A)
        val PrimaryContainer = Color(0xFF005313)
        val OnPrimaryContainer = Color(0xFF8AFB8C)

        val Secondary = Color(0xFFB5CCB7)
        val OnSecondary = Color(0xFF203526)
        val SecondaryContainer = Color(0xFF364B3B)
        val OnSecondaryContainer = Color(0xFFD0E8D2)

        val Tertiary = Color(0xFFA1CED5)
        val OnTertiary = Color(0xFF00363C)
        val TertiaryContainer = Color(0xFF1F4D53)
        val OnTertiaryContainer = Color(0xFFBCEBF1)

        val Error = Color(0xFFFFB4AB)
        val OnError = Color(0xFF690005)
        val ErrorContainer = Color(0xFF93000A)
        val OnErrorContainer = Color(0xFFFFDAD6)

        val Background = Color(0xFF1A1C19)
        val OnBackground = Color(0xFFE2E3DD)
        val Surface = Color(0xFF1A1C19)
        val OnSurface = Color(0xFFE2E3DD)
        val SurfaceVariant = Color(0xFF424940)
        val OnSurfaceVariant = Color(0xFFC2C9BD)
        val SurfaceTint = Color(0xFF6EDD72)

        val Outline = Color(0xFF8C9388)
        val OutlineVariant = Color(0xFF424940)

        val InverseSurface = Color(0xFFE2E3DD)
        val InverseOnSurface = Color(0xFF2F312D)
        val InversePrimary = Color(0xFF1B5E20)

        val Scrim = Color(0xFF000000)

        val Income = Color(0xFF6FCB9F)
        val Expense = Color(0xFFE89B98)
        val Transfer = Color(0xFF8FB0E0)

        /** Same amber, lifted to the mockup's dark-scheme lightness (`oklch(78% 0.13 68)`). */
        val Warning = Color(0xFFE9B45E)

        /**
         * [AppColors.CategoryTints] with the same hues taken to the lightness the dark scheme's
         * other accents sit at (HSL L 72% / S 55% — the rule that already produces [Expense] and
         * [Transfer] from their light counterparts). The light tints are mid-dark on the dark
         * surface, both as the icon tint and as the 18% wash behind it in `SurferCategoryBubble`.
         *
         * Index 2 (indigo) and index 6 (clay) are the same colours as [Transfer] and [Expense]
         * in the light palette, so they reuse the dark values of those accents verbatim.
         */
        val CategoryTints: List<Color> = listOf(
            Color(0xFF90DFC4), // mint
            Color(0xFFDFA090), // amber
            Transfer, //          indigo
            Color(0xFFDFBE90), // citrus
            Color(0xFFAF90DF), // plum
            Color(0xFFDF90A9), // rose
            Expense, //           clay
            Color(0xFFC9DF90), // moss
        )
    }
}
