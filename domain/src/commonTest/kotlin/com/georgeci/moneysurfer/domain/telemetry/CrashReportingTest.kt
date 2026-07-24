package com.georgeci.moneysurfer.domain.telemetry

import co.touchlab.kermit.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext

private const val CollectorSettleMillis = 200L

/** Lets the process-scoped uid collector catch up with the flow it observes. */
private suspend fun settleCollector() {
    withContext(Dispatchers.Default) { delay(CollectorSettleMillis) }
}

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

    "installing twice does not stack uid collectors" {
        val reporter = FakeCrashReporter()
        val uids = MutableStateFlow<String?>("uid-1")

        installCrashReporting(reporter, isDebug = false, userIds = uids)
        installCrashReporting(reporter, isDebug = false, userIds = uids)
        // The collector runs on the install's own process scope (Dispatchers.Default),
        // so let it settle rather than driving a scheduler we don't own. Settling before
        // the second value also keeps StateFlow from conflating the initial one away.
        settleCollector()
        uids.value = "uid-2"
        settleCollector()

        // A stacked second collector would have doubled both.
        reporter.userIds shouldContainExactly listOf("uid-1", "uid-2")
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
