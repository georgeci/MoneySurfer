package com.georgeci.moneysurfer.domain.preferences

interface UiPreferences {
    val isDynamicColorAvailable: Boolean

    /**
     * `false` until the first-launch onboarding screen is finished. Device-scoped — survives
     * logout, reset only by reinstall / clear-data.
     */
    val onboardingCompleted: Pref<Boolean>

    val paletteSource: Pref<PaletteSource>
    val themeMode: Pref<ThemeMode>
    val containerStyle: Pref<ContainerStyle>
}
