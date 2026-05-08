package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationFeatureConfig
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Locks in the offline transaction-creation surface: the Transfer segment must not be
 * reachable. The shape-only `verify()` test in [OfflineKoinModuleVerificationTest] would
 * still pass if a future refactor silently flipped `transferEnabled` back to true (e.g.
 * by re-adding a default binding that loads after `offlineWiring`). This test resolves
 * the binding for real and asserts it wins.
 */
class OfflineTransactionCreationFeatureConfigTest {

    private val app = koinApplication {
        modules(offlineWiring)
    }

    @AfterTest
    fun tearDown() {
        app.close()
    }

    @Test
    fun `offline build resolves TransactionCreationFeatureConfig with transfer disabled`() {
        val config = app.koin.get<TransactionCreationFeatureConfig>()
        assertFalse(config.transferEnabled, "offline build hides Transfer in transaction creation")
    }
}
