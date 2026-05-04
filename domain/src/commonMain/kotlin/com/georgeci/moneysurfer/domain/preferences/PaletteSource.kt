package com.georgeci.moneysurfer.domain.preferences

/**
 * Source of the active accent colour for the Material 3 theme.
 *
 * - [Brand]: app's static brand palette (no seed, uses tokens from `uikit`).
 * - [Preset]: one of the predefined accent seeds shipped with the app.
 *             The full scheme is generated from the seed via HCT.
 * - [Dynamic]: derived from the OS wallpaper (Android 12+ Material You).
 */
sealed interface PaletteSource {
    data object Brand : PaletteSource
    data class Preset(val seed: AccentSeed) : PaletteSource
    data object Dynamic : PaletteSource

    companion object {
        val DEFAULT: PaletteSource = Brand
    }
}
