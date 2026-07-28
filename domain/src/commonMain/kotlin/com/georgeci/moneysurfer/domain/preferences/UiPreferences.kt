package com.georgeci.moneysurfer.domain.preferences

import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import kotlinx.coroutines.flow.Flow

interface UiPreferences {
    val isDynamicColorAvailable: Boolean

    /**
     * `false` until the first-launch onboarding screen is finished. Device-scoped — survives
     * logout, reset only by reinstall / clear-data.
     */
    val onboardingCompleted: Pref<Boolean>

    /**
     * The stored choice, exactly as written — including [PaletteSource.Dynamic] on a platform with
     * no Material You. The picker binds here and simply omits the Dynamic option where it is
     * unavailable: nothing is highlighted, and no write happens unless the user picks something.
     * Theming reads [effectivePaletteSource] instead.
     */
    val paletteSource: Pref<PaletteSource>

    /**
     * [paletteSource] clamped to what this platform can render.
     *
     * Kept separate from the stored value rather than clamping the `Pref` itself: on a desktop a
     * clamped `Pref` would render `Brand` as the current selection, and the first tap would write
     * `Brand` over the `Dynamic` the user set on their phone.
     */
    val effectivePaletteSource: Flow<PaletteSource>

    val themeMode: Pref<ThemeMode>
    val containerStyle: Pref<ContainerStyle>

    /**
     * Date window the transactions list opens on. Persisted rather than held in the screen's
     * state so the choice survives process death and app restarts; the position *inside* that
     * period is deliberately not persisted — the list always reopens on the current one.
     */
    val transactionsPeriodMode: Pref<TransactionPeriodMode>

    /**
     * Order, visibility and card style of the dashboard widgets. Device-local in v1: the layout
     * describes the screen in the user's hand, so it is deliberately not synced through Firestore
     * (a phone layout would be wrong on a tablet). Migrating it to the workspace later is a
     * matter of swapping the binding behind this `Pref`.
     */
    val dashboardLayout: Pref<DashboardLayoutConfig>

    /**
     * Everything below backs the Preferences screen (issue #370). The screen stores the choices and
     * renders them back; nothing else reads them yet — the locale, calendar and behaviour features
     * they describe are not built. They are declared here rather than kept in the ViewModel so the
     * choice survives process death, and so the consumers, when they arrive, have one place to read
     * from.
     *
     * All of them are device-local (`sync = false`). Replication of user settings is its own
     * concern (#334) and none of these has a consumer to disagree about yet, so nothing is gained
     * by putting a half-built key on the wire.
     */
    val appLanguage: Pref<AppLanguage>

    val appRegion: Pref<AppRegion>

    /**
     * Currency new accounts and cross-account totals should default to. Defaults to the platform
     * locale's currency, so a fresh install is already right for most users without a first-run
     * question.
     */
    val defaultCurrency: Pref<CurrencyCode>

    val numberFormat: Pref<NumberFormatStyle>
    val weekStart: Pref<WeekStart>
    val hourFormat: Pref<HourFormat>
    val defaultTransactionType: Pref<DefaultTransactionType>

    /** Blur balances on the dashboard until tapped. */
    val hideAmounts: Pref<Boolean>

    /** Suggest a category from the merchant when a transaction is created. */
    val autoCategorize: Pref<Boolean>

    /** Round purchases up and move the difference to savings. */
    val roundUpSavings: Pref<Boolean>
}
