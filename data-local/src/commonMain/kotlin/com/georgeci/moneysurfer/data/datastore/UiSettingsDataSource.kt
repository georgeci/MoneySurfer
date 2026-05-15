package com.georgeci.moneysurfer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.data.datastore.core.PreferenceStore
import org.koin.core.annotation.Single

@Single
class UiSettingsDataSource(
    dataStore: DataStore<Preferences>,
) {
    private val prefs = PreferenceStore(dataStore)

    val currentUserId = prefs.string(
        name = "session.current_user_id",
        default = "",
    )

    val currentWorkspaceId = prefs.string(
        name = "session.current_workspace_id",
        default = "",
    )

    /**
     * Firebase auth uid. Set when a Firebase-backed flow (login/signup/anon) succeeds, cleared
     * on logout. Stays empty for local-only sessions (demo). Lets callers detect "is there a
     * Firebase session right now?" without round-tripping through the auth SDK.
     */
    val firebaseUid = prefs.string(
        name = "session.firebase_uid",
        default = "",
    )

    val paletteSource = prefs.string(
        name = "ui.palette_source",
        default = "BRAND",
    )

    val themeMode = prefs.string(
        name = "ui.theme_mode",
        default = "System",
    )

    val containerStyle = prefs.string(
        name = "ui.container_style",
        default = "Card",
    )

    /**
     * Sticky flag set by `DemoLoginUseCase` and cleared by `WipeDemoDataUseCase`.
     * The auth flow consults it on real login/signup to decide whether to wipe
     * orphan demo data before bootstrapping the real session. Demo data must
     * never reach Firestore — see sync.md §2.11.
     */
    val hasUsedDemo = prefs.boolean(
        name = "session.has_used_demo",
        default = false,
    )

    /**
     * `true` by default — only the offline first-run seed flips it to `false` after
     * auto-creating a workspace with a locale-derived currency. `AppLaunchViewModel`
     * routes to the currency picker while this is `false`; the picker flips it back.
     */
    val currencyChosen = prefs.boolean(
        name = "session.currency_chosen",
        default = true,
    )
}
