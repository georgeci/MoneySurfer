package com.georgeci.moneysurfer.domain.preferences

interface UiPreferences {
    val isDynamicColorAvailable: Boolean
    val paletteSource: Pref<PaletteSource>
    val themeMode: Pref<ThemeMode>
    val containerStyle: Pref<ContainerStyle>

    /**
     * Date window the transactions list opens on. Persisted rather than held in the screen's
     * state so the choice survives process death and app restarts; the position *inside* that
     * period is deliberately not persisted — the list always reopens on the current one.
     */
    val transactionsPeriodMode: Pref<TransactionPeriodMode>
}
