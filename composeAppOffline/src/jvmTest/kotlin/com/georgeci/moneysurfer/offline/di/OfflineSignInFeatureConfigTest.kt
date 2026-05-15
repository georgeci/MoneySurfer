package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.feature.login.SignInFeatureConfig
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the offline-only sign-in UI. The shape-only `verify()` test in
 * [OfflineKoinModuleVerificationTest] would still pass if a future refactor
 * silently switched `demoOnly` back to false (e.g. by re-adding a default
 * binding in `LoginModule` that loads after `offlineWiring`). This test
 * resolves the binding for real and asserts it wins.
 */
class OfflineSignInFeatureConfigTest {

    private val app = koinApplication {
        modules(offlineWiring)
    }

    @AfterTest
    fun tearDown() {
        app.close()
    }

    @Test
    fun `offline build resolves SignInFeatureConfig with only demo enabled`() {
        val config = app.koin.get<SignInFeatureConfig>()
        assertFalse(config.emailPassword, "offline build has no remote auth backend")
        assertFalse(config.anonymous, "offline build has no remote auth backend")
        assertTrue(config.demo, "offline build must keep the demo entry point")
        assertTrue(config.demoOnly, "offline build must render only the demo CTA")
    }

    @Test
    fun `offline build resolves SyncFeatureFlag disabled`() {
        val flag = app.koin.get<SyncFeatureFlag>()
        assertFalse(flag.enabled, "offline build has no sync backend — flag must stay off")
    }
}
