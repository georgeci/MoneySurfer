package com.georgeci.moneysurfer.domain.telemetry

import co.touchlab.kermit.Severity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class CrashReportingLogWriterTest : StringSpec({

    "everything below Warn is dropped" {
        val reporter = FakeCrashReporter()
        val writer = CrashReportingLogWriter(reporter)

        listOf(Severity.Verbose, Severity.Debug, Severity.Info).forEach { severity ->
            writer.isLoggable(tag = "Any", severity = severity) shouldBe false
        }
    }

    "Warn and above are loggable" {
        val reporter = FakeCrashReporter()
        val writer = CrashReportingLogWriter(reporter)

        listOf(Severity.Warn, Severity.Error, Severity.Assert).forEach { severity ->
            writer.isLoggable(tag = "Any", severity = severity) shouldBe true
        }
    }

    "a warning becomes a breadcrumb but not a non-fatal" {
        val reporter = FakeCrashReporter()

        CrashReportingLogWriter(reporter).log(
            severity = Severity.Warn,
            message = "sync retry scheduled",
            tag = "SyncEngine",
            throwable = IllegalStateException("boom"),
        )

        reporter.breadcrumbs shouldContainExactly listOf("WARN/SyncEngine: sync retry scheduled")
        reporter.recorded.shouldBeEmpty()
    }

    "an error with a throwable is recorded as a non-fatal" {
        val reporter = FakeCrashReporter()
        val failure = IllegalStateException("boom")

        CrashReportingLogWriter(reporter).log(
            severity = Severity.Error,
            message = "load failed",
            tag = "DashboardViewModel",
            throwable = failure,
        )

        reporter.breadcrumbs shouldContainExactly listOf("ERROR/DashboardViewModel: load failed")
        reporter.recorded shouldContainExactly listOf(failure)
    }

    "an error without a throwable only leaves a breadcrumb" {
        val reporter = FakeCrashReporter()

        CrashReportingLogWriter(reporter).log(
            severity = Severity.Error,
            message = "load failed",
            tag = "",
            throwable = null,
        )

        reporter.breadcrumbs shouldContainExactly listOf("ERROR/NoTag: load failed")
        reporter.recorded.shouldBeEmpty()
    }
})
