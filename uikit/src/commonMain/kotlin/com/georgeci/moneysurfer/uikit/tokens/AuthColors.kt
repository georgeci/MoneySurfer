package com.georgeci.moneysurfer.uikit.tokens

import androidx.compose.ui.graphics.Color

/**
 * Fixed brand palette for the pre-auth screens (onboarding + sign-in), which stay MoneySurfer
 * green regardless of the user's accent seed — the theme picker only starts applying once they're
 * inside the app. Because these screens deliberately opt out of [AppColors]-derived theming, their
 * colours live here as tokens rather than as literals in the feature module.
 */
object AuthColors {
    val GreenTop = Color(0xFF7ED321)
    val GreenBottom = Color(0xFF5FB011)
    val Ink = Color(0xFF0F2E12)
    val OnBrand = Color(0xFFFFFFFF)
    val OnBrandMuted = Color(0xE6FFFFFF)
    val OnBrandSubtle = Color(0xCCFFFFFF)
    val Sheet = Color(0xFFFFFFFF)
    val SheetMuted = Color(0xFF445844)
    val SheetSubtle = Color(0xFF7A8C7B)
    val Divider = Color(0xFFD6E4D7)
    val PrimaryDark = Color(0xFF1B5E20)
    val GreenSoft = Color(0xFFE8F3E9)
    val Mint = Color(0xFF2E9A6A)
    val BrandTile = Color(0x29FFFFFF)
    val ProgressRest = Color(0x47FFFFFF)
    val WaveBack = Color(0xFF1B5E20)
    val WaveMid = Color(0xFF2E7D32)
    val WaveFront = Color(0xFF2E9A3E)
}
