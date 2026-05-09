package com.georgeci.moneysurfer.sync.internal.network

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class NoOpNetworkMonitorSpec : StringSpec({

    "NoOpNetworkMonitor reports always-online" {
        runTest {
            val monitor = NoOpNetworkMonitor()
            monitor.online.value shouldBe true
            monitor.online.first() shouldBe true
        }
    }
})
