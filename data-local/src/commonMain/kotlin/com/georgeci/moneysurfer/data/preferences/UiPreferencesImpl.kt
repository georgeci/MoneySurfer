package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.appconfig.Config
import com.georgeci.moneysurfer.data.config.UiConfigKeys
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
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
}
