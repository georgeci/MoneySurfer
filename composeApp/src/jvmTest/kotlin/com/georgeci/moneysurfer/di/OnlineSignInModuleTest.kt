package com.georgeci.moneysurfer.di

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors `composeAppOffline`'s `OfflineSignInFeatureConfigTest`: locks in the
 * online sign-in surface so a future refactor can't quietly drop one of the
 * auth entry points (e.g. switch `anonymous` off) without a failing test.
 */
class OnlineSignInModuleTest {

    @Test
    fun `online build registers SignInFeatureConfig with every entry point enabled`() {
        val config = OnlineSignInModule().signInFeatureConfig()

        assertTrue(config.emailPassword)
        assertTrue(config.anonymous)
        assertTrue(config.demo)
        assertFalse(config.demoOnly)
    }

    @Test
    fun `online build registers OfflineBuildFlags with isOffline false`() {
        val flags = OnlineSignInModule().offlineBuildFlags()

        assertFalse(flags.isOffline)
    }

    @Test
    fun `online build registers SyncFeatureFlag disabled by default`() {
        val flag = OnlineSignInModule().syncFeatureFlag()

        assertFalse(flag.enabled, "sync is hidden by default in the online build")
    }
}
