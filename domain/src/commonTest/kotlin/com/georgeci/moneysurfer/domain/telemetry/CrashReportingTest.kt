package com.georgeci.moneysurfer.domain.telemetry

import co.touchlab.kermit.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

class CrashReportingTest : StringSpec({

    // Kermit config and the install latch are process-wide; restore both so we don't
    // leave a live writer (or a tripped latch) behind for other specs.
    val originalConfig = Logger.config
    beforeTest { resetCrashReportingForTest() }
    afterTest {
        Logger.setLogWriters(originalConfig.logWriterList)
        resetCrashReportingForTest()
    }

    "release builds enable collection" {
        val reporter = FakeCrashReporter()

        installCrashReporting(reporter, isDebug = false)

        reporter.isCollectionEnabled shouldBe true
    }

    "debug builds keep collection off" {
        val reporter = FakeCrashReporter()

        installCrashReporting(reporter, isDebug = true)

        reporter.isCollectionEnabled shouldBe false
    }

    "installing twice does not stack writers" {
        val reporter = FakeCrashReporter()
        val writersBefore = Logger.config.logWriterList.size

        installCrashReporting(reporter, isDebug = false)
        installCrashReporting(reporter, isDebug = false)

        Logger.config.logWriterList.size shouldBe writersBefore + 1
    }

    "an installed reporter receives logged errors" {
        val reporter = FakeCrashReporter()
        val failure = IllegalStateException("boom")

        installCrashReporting(reporter, isDebug = false)
        Logger.withTag("Spec").e(failure) { "it broke" }

        reporter.recorded shouldContainExactly listOf(failure)
    }

    "the user id follows the session" {
        val reporter = FakeCrashReporter()
        val uids = MutableStateFlow<String?>(null)
        val scope = TestScope(StandardTestDispatcher())

        bindCrashReportingUser(reporter, uids, scope)
        scope.runCurrent()
        uids.value = "uid-1"
        scope.runCurrent()
        uids.value = null
        scope.runCurrent()

        reporter.userIds shouldContainExactly listOf(null, "uid-1", null)
    }
})
