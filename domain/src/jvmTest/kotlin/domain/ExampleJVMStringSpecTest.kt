package com.georgeci.moneysurfer.domain

import app.cash.turbine.test
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

class ExampleJVMStringSpecTest : StringSpec({

    "addition should work" {
        (1 + 2) shouldBe 3
    }

    "flow should emit expected values" {
        flowOf(1, 2, 3).test {
            awaitItem() shouldBe 1
            awaitItem() shouldBe 2
            awaitItem() shouldBe 3
            awaitComplete()
        }
    }
})
