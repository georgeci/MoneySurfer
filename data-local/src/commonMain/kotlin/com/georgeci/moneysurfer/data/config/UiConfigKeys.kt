package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import org.koin.core.annotation.Single

/**
 * Keys behind [UiPreferencesImpl][com.georgeci.moneysurfer.data.preferences.UiPreferencesImpl].
 *
 * `internal` so no feature can reach a key directly — name, default and codec now live in one
 * place instead of being spread across a DataStore declaration, a per-field adapter and an
 * interface.
 */
internal object UiConfigKeys {

    val themeMode: SettingKey<ThemeMode> =
        SettingKey.enum("ui.theme_mode", ThemeMode.System, sync = true)

    val paletteSource: SettingKey<PaletteSource> =
        SettingKey.custom("ui.palette_source", PaletteSource.DEFAULT, PaletteSourceCodec, sync = true)

    val containerStyle: SettingKey<ContainerStyle> =
        SettingKey.enum("ui.container_style", ContainerStyle.Card, sync = true)

    val transactionsPeriodMode: SettingKey<TransactionPeriodMode> =
        SettingKey.enum("ui.transactions_period_mode", TransactionPeriodMode.DEFAULT, sync = true)

    /**
     * `sync = false` is mandatory: replicating this would replay onboarding on every other device,
     * and resetting it on logout would replay it after every logout. Being device-scoped is also
     * what keeps it in DataStore rather than in the account-scoped `config_entry` table the wipe
     * clears.
     */
    val onboardingCompleted: SettingKey<Boolean> =
        SettingKey.bool("ui.onboarding_completed", default = false, sync = false)

    /** Device-local by design — a phone layout would be wrong on a tablet. */
    val dashboardLayout: SettingKey<DashboardLayoutConfig> = SettingKey.custom(
        "ui.dashboard_layout",
        DashboardLayoutConfig.DEFAULT,
        DashboardLayoutConfigCodec,
        sync = false,
    )

    val all: List<ConfigKey<*>> = listOf(
        themeMode,
        paletteSource,
        containerStyle,
        transactionsPeriodMode,
        onboardingCompleted,
        dashboardLayout,
    )
}

/**
 * One class per group, bound to the shared interface. Several modules binding a bare
 * `ConfigKeyGroup` would overwrite one another in Koin's definition index and `getAll()` would
 * return a single surviving group, silently hiding most keys from the debug panel.
 */
@Single(binds = [ConfigKeyGroup::class])
class UiConfigKeyGroup : ConfigKeyGroup {
    override val keys: List<ConfigKey<*>> = UiConfigKeys.all
}
