package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.preferences.AppLanguage
import com.georgeci.moneysurfer.domain.preferences.AppRegion
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.DefaultTransactionType
import com.georgeci.moneysurfer.domain.preferences.HourFormat
import com.georgeci.moneysurfer.domain.preferences.NumberFormatStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.preferences.WeekStart
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [UiPreferences] for ViewModel tests. Every pref is a real `Pref.inMemory`, so writes
 * are observable exactly as they would be through DataStore — reading `flow.first()` back tells
 * you what would have survived process death.
 *
 * The one fixture for every module: adding a field to [UiPreferences] should not break unrelated
 * tests, which is what the hand-written fakes in `feature/login`, `feature/settings` and
 * `feature/transaction` used to do.
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
    appLanguage: AppLanguage = AppLanguage.DEFAULT,
    appRegion: AppRegion = AppRegion.DEFAULT,
    /**
     * Fixed rather than `CurrencyDefaults.systemDefault()`: a fixture whose default depends on the
     * machine's locale makes an assertion pass on one CI runner and fail on another.
     */
    defaultCurrency: CurrencyCode = CurrencyCode("EUR"),
    numberFormat: NumberFormatStyle = NumberFormatStyle.DEFAULT,
    weekStart: WeekStart = WeekStart.DEFAULT,
    hourFormat: HourFormat = HourFormat.DEFAULT,
    defaultTransactionType: DefaultTransactionType = DefaultTransactionType.DEFAULT,
    hideAmounts: Boolean = false,
    autoCategorize: Boolean = true,
    roundUpSavings: Boolean = false,
) : UiPreferences {
    override val onboardingCompleted: Pref<Boolean> = Pref.inMemory(onboardingCompleted)
    override val paletteSource: Pref<PaletteSource> = Pref.inMemory(paletteSource)

    /** Same clamp the real implementation applies, so a test can assert either side of it. */
    override val effectivePaletteSource: Flow<PaletteSource> = this.paletteSource.flow.map { stored ->
        if (stored is PaletteSource.Dynamic && !isDynamicColorAvailable) PaletteSource.DEFAULT else stored
    }

    override val themeMode: Pref<ThemeMode> = Pref.inMemory(themeMode)
    override val containerStyle: Pref<ContainerStyle> = Pref.inMemory(containerStyle)
    override val transactionsPeriodMode: Pref<TransactionPeriodMode> = Pref.inMemory(transactionsPeriodMode)
    override val dashboardLayout: Pref<DashboardLayoutConfig> = Pref.inMemory(dashboardLayout)
    override val appLanguage: Pref<AppLanguage> = Pref.inMemory(appLanguage)
    override val appRegion: Pref<AppRegion> = Pref.inMemory(appRegion)
    override val defaultCurrency: Pref<CurrencyCode> = Pref.inMemory(defaultCurrency)
    override val numberFormat: Pref<NumberFormatStyle> = Pref.inMemory(numberFormat)
    override val weekStart: Pref<WeekStart> = Pref.inMemory(weekStart)
    override val hourFormat: Pref<HourFormat> = Pref.inMemory(hourFormat)
    override val defaultTransactionType: Pref<DefaultTransactionType> = Pref.inMemory(defaultTransactionType)
    override val hideAmounts: Pref<Boolean> = Pref.inMemory(hideAmounts)
    override val autoCategorize: Pref<Boolean> = Pref.inMemory(autoCategorize)
    override val roundUpSavings: Pref<Boolean> = Pref.inMemory(roundUpSavings)
}
