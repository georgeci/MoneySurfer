package com.georgeci.moneysurfer.sync.scheduler

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncConstraintsSpec : StringSpec({

    "default constraints disable network/charging gates and require non-low battery" {
        val constraints = SyncConstraints()
        constraints.requireUnmeteredNetwork shouldBe false
        constraints.requireCharging shouldBe false
        constraints.requireBatteryNotLow shouldBe true
    }

    "explicit overrides are exposed" {
        val constraints = SyncConstraints(
            requireUnmeteredNetwork = true,
            requireCharging = true,
            requireBatteryNotLow = false,
        )
        constraints.requireUnmeteredNetwork shouldBe true
        constraints.requireCharging shouldBe true
        constraints.requireBatteryNotLow shouldBe false
    }

    "copy preserves equality" {
        val original = SyncConstraints()
        original.copy() shouldBe original
    }
})
