package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Locks in the offline-only flag wiring. Verification only walks the graph
 * shape — this test resolves [OfflineBuildFlags] for real and asserts the
 * offline override wins, so a future module-load reordering can't silently
 * regress the gate that hides Backup/Sync/Members/Invite/Logout from the
 * offline UI.
 */
class OfflineBuildFlagsTest {

    private val app = koinApplication {
        modules(offlineWiring)
    }

    @AfterTest
    fun tearDown() {
        app.close()
    }

    @Test
    fun `offline build resolves OfflineBuildFlags with isOffline true`() {
        val flags = app.koin.get<OfflineBuildFlags>()
        assertTrue(flags.isOffline, "offline host must register isOffline = true")
    }
}
