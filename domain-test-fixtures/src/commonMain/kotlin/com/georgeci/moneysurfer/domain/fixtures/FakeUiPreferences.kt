package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences

/**
 * In-memory [UiPreferences] for ViewModel tests. Every pref is a real `Pref.inMemory`, so writes
 * are observable exactly as they would be through DataStore — reading `flow.first()` back tells
 * you what would have survived process death.
 */
open class FakeUiPreferences(
    override val isDynamicColorAvailable: Boolean = false,
    /** Fresh install by default — the onboarding screen has not run yet. */
    onboardingCompleted: Boolean = false,
    paletteSource: PaletteSource = PaletteSource.Brand,
    themeMode: ThemeMode = ThemeMode.System,
    containerStyle: ContainerStyle = ContainerStyle.Card,
    transactionsPeriodMode: TransactionPeriodMode = TransactionPeriodMode.DEFAULT,
    dashboardLayout: DashboardLayoutConfig = DashboardLayoutConfig.DEFAULT,
) : UiPreferences {
    override val onboardingCompleted: Pref<Boolean> = Pref.inMemory(onboardingCompleted)
    override val paletteSource: Pref<PaletteSource> = Pref.inMemory(paletteSource)
    override val themeMode: Pref<ThemeMode> = Pref.inMemory(themeMode)
    override val containerStyle: Pref<ContainerStyle> = Pref.inMemory(containerStyle)
    override val transactionsPeriodMode: Pref<TransactionPeriodMode> = Pref.inMemory(transactionsPeriodMode)
    override val dashboardLayout: Pref<DashboardLayoutConfig> = Pref.inMemory(dashboardLayout)
}
