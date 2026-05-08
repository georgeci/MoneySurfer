package com.georgeci.moneysurfer.di

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Mirrors `composeAppOffline`'s `OfflineTransactionCreationFeatureConfigTest`: locks in
 * the online transaction-creation surface so a future refactor can't quietly drop the
 * Transfer segment from the online build.
 */
class OnlineTransactionCreationModuleTest {

    @Test
    fun `online build registers TransactionCreationFeatureConfig with transfer enabled`() {
        val config = OnlineTransactionCreationModule().transactionCreationFeatureConfig()

        assertTrue(config.transferEnabled)
    }
}
