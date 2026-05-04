package com.georgeci.moneysurfer.domain.preferences

interface UiPreferences {
    val isDynamicColorAvailable: Boolean
    val paletteSource: Pref<PaletteSource>
    val themeMode: Pref<ThemeMode>
    val containerStyle: Pref<ContainerStyle>
}
