package com.georgeci.moneysurfer.domain.preferences

import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
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
}
