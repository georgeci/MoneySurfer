package com.georgeci.moneysurfer.feature.transaction.creation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TransactionCreationFeatureConfigTest : StringSpec({

    "defaults enable transfer" {
        TransactionCreationFeatureConfig().transferEnabled shouldBe true
    }

    "transfer can be disabled for the offline build" {
        TransactionCreationFeatureConfig(transferEnabled = false).transferEnabled shouldBe false
    }
})
