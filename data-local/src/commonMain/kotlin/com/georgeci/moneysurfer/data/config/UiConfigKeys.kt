package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.formatter.CurrencyDefaults
import com.georgeci.moneysurfer.domain.preferences.AppLanguage
import com.georgeci.moneysurfer.domain.preferences.AppRegion
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.DefaultTransactionType
import com.georgeci.moneysurfer.domain.preferences.HourFormat
import com.georgeci.moneysurfer.domain.preferences.NumberFormatStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.WeekStart
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
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
     * and resetting it on logout would replay it after every logout. It therefore stays in
     * DataStore when the per-user-sync issue moves the `sync = true` keys into the account-scoped
     * Room table.
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

    /**
     * Preferences-screen keys (issue #370). All `sync = false`: replicating user settings is its
     * own concern (#334), and none of these has a consumer yet, so there is nothing for two devices
     * to disagree about — the screen stores the choice and reads it back, and that is all.
     */
    val appLanguage: SettingKey<AppLanguage> =
        SettingKey.enum("ui.app_language", AppLanguage.DEFAULT, sync = false)

    val appRegion: SettingKey<AppRegion> =
        SettingKey.enum("ui.app_region", AppRegion.DEFAULT, sync = false)

    /**
     * The one key whose default is resolved at runtime rather than written down: the currency the
     * platform locale implies is a far better first guess than any constant, and it is the same
     * seed account creation already uses. `CurrencyDefaults` is a pure locale lookup with a `USD`
     * fallback, so this stays a plain value and never becomes a DI participant.
     */
    val defaultCurrency: SettingKey<CurrencyCode> = SettingKey.custom(
        "ui.default_currency",
        CurrencyDefaults.systemDefault(),
        CurrencyCodeCodec,
        sync = false,
    )

    val numberFormat: SettingKey<NumberFormatStyle> =
        SettingKey.enum("ui.number_format", NumberFormatStyle.DEFAULT, sync = false)

    val weekStart: SettingKey<WeekStart> =
        SettingKey.enum("ui.week_start", WeekStart.DEFAULT, sync = false)

    val hourFormat: SettingKey<HourFormat> =
        SettingKey.enum("ui.hour_format", HourFormat.DEFAULT, sync = false)

    val defaultTransactionType: SettingKey<DefaultTransactionType> =
        SettingKey.enum("ui.default_transaction_type", DefaultTransactionType.DEFAULT, sync = false)

    val hideAmounts: SettingKey<Boolean> =
        SettingKey.bool("ui.hide_amounts", default = false, sync = false)

    val autoCategorize: SettingKey<Boolean> =
        SettingKey.bool("ui.auto_categorize", default = true, sync = false)

    val roundUpSavings: SettingKey<Boolean> =
        SettingKey.bool("ui.round_up_savings", default = false, sync = false)

    val all: List<ConfigKey<*>> = listOf(
        themeMode,
        paletteSource,
        containerStyle,
        transactionsPeriodMode,
        onboardingCompleted,
        dashboardLayout,
        appLanguage,
        appRegion,
        defaultCurrency,
        numberFormat,
        weekStart,
        hourFormat,
        defaultTransactionType,
        hideAmounts,
        autoCategorize,
        roundUpSavings,
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
