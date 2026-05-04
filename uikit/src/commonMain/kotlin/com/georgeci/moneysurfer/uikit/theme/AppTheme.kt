package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme as materialKolorScheme

/**
 * Style used by `materialKolor` to expand a single seed colour into the full M3 colour scheme.
 * Acts as an app-wide knob — flip the value here and every seed-based palette regenerates.
 *
 * Options (selected): `TonalSpot` (Material You default), `Vibrant`, `Expressive`, `Fidelity`,
 * `Content`, `Neutral`, `Monochrome`, `Rainbow`, `FruitSalad`. See `PaletteStyle` KDoc for
 * the visual differences each strategy produces.
 */
internal val SeedPaletteStyle: PaletteStyle = PaletteStyle.TonalSpot

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color? = null,
    dynamicColor: Boolean = false,
    containerStyle: SurferContainerStyle = SurferContainerStyle.Card,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && isDynamicColorAvailable -> dynamicColorScheme(darkTheme)
        seedColor != null -> schemeFromSeed(seedColor, darkTheme)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

//    ConfigureSystemBars(darkTheme = darkTheme)

    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalContainerStyle provides containerStyle,
        LocalSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Builds a full Material 3 colour scheme from a single seed via HCT tonal palettes
 * (Hue/Chroma/Tone). Powered by `material-kolor`, the KMP port of Google's
 * `material-color-utilities`. `TonalSpot` is the default style used by Material You.
 */
private fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme =
    materialKolorScheme(
        seedColor = seed,
        isDark = dark,
        style = SeedPaletteStyle,
    )

object AppTheme {
    val materialColors: ColorScheme
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = MaterialTheme.shapes

    val spacing: Spacing
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSpacing.current

    val containerStyle: SurferContainerStyle
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalContainerStyle.current

    val semanticColors: SemanticColors
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSemanticColors.current
}
