package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.appconfig.Config
import com.georgeci.moneysurfer.data.config.UiConfigKeys
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
import org.koin.core.annotation.Single
import com.georgeci.moneysurfer.data.datastore.isDynamicColorAvailable as platformIsDynamicColorAvailable

/**
 * Every preference is a handle on the configuration engine now: the read chain is one hop shorter
 * than the old `PreferenceStore` → `UiSettingsDataSource` → `PrefAdapters` path, and the per-field
 * `runCatching { Enum.valueOf(...) }` blocks collapsed into the keys' codecs.
 */
@Single(binds = [UiPreferences::class])
class UiPreferencesImpl(config: Config) : UiPreferences {

    override val isDynamicColorAvailable: Boolean = platformIsDynamicColorAvailable

    override val onboardingCompleted: Pref<Boolean> = config.handle(UiConfigKeys.onboardingCompleted)

    override val paletteSource: Pref<PaletteSource> = config.handle(UiConfigKeys.paletteSource)

    /**
     * `Dynamic` needs Material You, which only Android has — but the palette is synced, so a phone
     * can legitimately hand a desktop a value it cannot render. Clamping happens here, on read, and
     * never inside a layer: a layer that rewrote values would make `Config.resolve` lie to the debug
     * panel.
     */
    override val effectivePaletteSource: Flow<PaletteSource> = paletteSource.flow.map { stored ->
        if (stored is PaletteSource.Dynamic && !isDynamicColorAvailable) PaletteSource.DEFAULT else stored
    }

    override val themeMode: Pref<ThemeMode> = config.handle(UiConfigKeys.themeMode)

    override val containerStyle: Pref<ContainerStyle> = config.handle(UiConfigKeys.containerStyle)

    override val transactionsPeriodMode: Pref<TransactionPeriodMode> =
        config.handle(UiConfigKeys.transactionsPeriodMode)

    override val dashboardLayout: Pref<DashboardLayoutConfig> = config.handle(UiConfigKeys.dashboardLayout)

    override val appLanguage: Pref<AppLanguage> = config.handle(UiConfigKeys.appLanguage)

    override val appRegion: Pref<AppRegion> = config.handle(UiConfigKeys.appRegion)

    override val defaultCurrency: Pref<CurrencyCode> = config.handle(UiConfigKeys.defaultCurrency)

    override val numberFormat: Pref<NumberFormatStyle> = config.handle(UiConfigKeys.numberFormat)

    override val weekStart: Pref<WeekStart> = config.handle(UiConfigKeys.weekStart)

    override val hourFormat: Pref<HourFormat> = config.handle(UiConfigKeys.hourFormat)

    override val defaultTransactionType: Pref<DefaultTransactionType> =
        config.handle(UiConfigKeys.defaultTransactionType)

    override val hideAmounts: Pref<Boolean> = config.handle(UiConfigKeys.hideAmounts)

    override val autoCategorize: Pref<Boolean> = config.handle(UiConfigKeys.autoCategorize)

    override val roundUpSavings: Pref<Boolean> = config.handle(UiConfigKeys.roundUpSavings)
}
