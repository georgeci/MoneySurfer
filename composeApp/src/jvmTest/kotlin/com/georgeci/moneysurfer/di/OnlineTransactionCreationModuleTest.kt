package com.georgeci.moneysurfer.di

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Mirrors `composeAppOffline`'s `OfflineTransactionCreationFeatureConfigTest`: locks in
 * the online transaction-creation surface so a future refactor can't quietly drop the
 * Transfer segment from the online build.
 */
class OnlineTransactionCreationModuleTest : StringSpec({

    "online build registers TransactionCreationFeatureConfig with transfer enabled" {
        OnlineTransactionCreationModule().transactionCreationFeatureConfig().transferEnabled shouldBe true
    }
})
