package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationFeatureConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.koin.dsl.koinApplication

/**
 * Locks in the offline transaction-creation surface: the Transfer segment must not be
 * reachable. The shape-only `verify()` test in [OfflineKoinModuleVerificationTest] would
 * still pass if a future refactor silently flipped `transferEnabled` back to true (e.g.
 * by re-adding a default binding that loads after `offlineWiring`). This test resolves
 * the binding for real and asserts it wins.
 */
class OfflineTransactionCreationFeatureConfigTest : StringSpec({

    "offline build resolves TransactionCreationFeatureConfig with transfer disabled" {
        val app = koinApplication { modules(offlineWiring) }
        try {
            app.koin.get<TransactionCreationFeatureConfig>().transferEnabled shouldBe false
        } finally {
            app.close()
        }
    }
})
