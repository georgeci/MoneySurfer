package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.SettingKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Which store a key lands in is a correctness property, not a preference: `sync = true` moves it
 * into the account-scoped `config_entry` table, which the logout wipe empties. These are the keys
 * where getting that wrong is silent and expensive.
 */
class ConfigKeyScopeTest : StringSpec({

    "the sync toggle is device-scoped, so logging out cannot silently re-enable sync" {
        // It gates its own replication: `SyncCoordinatorWorkspaceSyncer` refuses to push while it
        // is false, so `false` can never reach Firestore. Account-scoped, it would be wiped on
        // logout and resolve back to the `true` default with nothing able to restore the user's
        // choice — a deliberate opt-out reverting to opt-in, and uploads starting again.
        SyncConfigKeys.userEnabled.sync shouldBe false
    }

    "the kill switch is not a SettingKey, so no client can write it" {
        (SyncConfigKeys.remoteEnabled is SettingKey) shouldBe false
        SyncConfigKeys.remoteEnabled.remoteOverridable shouldBe true
    }

    "onboarding stays device-scoped, so a logout cannot replay it" {
        UiConfigKeys.onboardingCompleted.sync shouldBe false
    }

    "the dashboard layout stays device-scoped — a phone layout is wrong on a tablet" {
        UiConfigKeys.dashboardLayout.sync shouldBe false
    }

    "the appearance settings are the ones that follow the account" {
        listOf(
            UiConfigKeys.themeMode,
            UiConfigKeys.paletteSource,
            UiConfigKeys.containerStyle,
            UiConfigKeys.transactionsPeriodMode,
        ).forEach { key -> key.sync shouldBe true }
    }
})
