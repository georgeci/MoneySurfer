package com.georgeci.moneysurfer.domain.logging

import co.touchlab.kermit.Severity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Drives the writer directly rather than through [co.touchlab.kermit.Logger]: `addLogWriter` has
 * no counterpart that removes one, so installing here would leak the buffer into every other spec
 * in the module.
 */
class DebugLogBufferTest : StringSpec({

    beforeTest { DebugLogBuffer.clear() }
    afterSpec { DebugLogBuffer.clear() }

    "warnings and errors are recorded, newest first" {
        DebugLogBuffer.logWriter.log(Severity.Warn, "first", "Sync", null)
        DebugLogBuffer.logWriter.log(Severity.Error, "second", "Sync", null)

        DebugLogBuffer.entries.value.map { it.message } shouldBe listOf("second", "first")
    }

    "the throwable travels with its entry" {
        val failure = IllegalStateException("boom")

        DebugLogBuffer.logWriter.log(Severity.Error, "upload failed", "Sync", failure)

        DebugLogBuffer.entries.value.single() shouldBe DebugLogEntry(
            id = 1,
            severity = Severity.Error,
            tag = "Sync",
            message = "upload failed",
            throwable = failure,
        )
    }

    // The severities that historically carried PII (issue #154) must not sit in a buffer a tester
    // can read on a device, so the floor is enforced by the writer itself, not only by Kermit's
    // global min-severity.
    "anything below Warn is not loggable" {
        val loggable = Severity.entries.filter { DebugLogBuffer.logWriter.isLoggable("Sync", it) }

        loggable shouldBe listOf(Severity.Warn, Severity.Error, Severity.Assert)
    }

    "the buffer keeps the newest 100 entries and drops the rest" {
        repeat(120) { index ->
            DebugLogBuffer.logWriter.log(Severity.Warn, "entry $index", "Sync", null)
        }

        val entries = DebugLogBuffer.entries.value
        entries.size shouldBe 100
        entries.first().message shouldBe "entry 119"
        entries.last().message shouldBe "entry 20"
    }

    // The panel keys its rows by id, so a collision would make Compose reuse one row's state for
    // another entry — and two identical warnings in a row is the ordinary case, not a corner one.
    "ids stay unique across the whole buffer, even for identical lines" {
        repeat(120) { DebugLogBuffer.logWriter.log(Severity.Warn, "same", "Sync", null) }

        val ids = DebugLogBuffer.entries.value.map { it.id }
        ids.toSet().size shouldBe ids.size
    }

    "ids restart after a clear, when nothing is left to collide with" {
        DebugLogBuffer.logWriter.log(Severity.Warn, "before", "Sync", null)
        DebugLogBuffer.clear()

        DebugLogBuffer.logWriter.log(Severity.Warn, "after", "Sync", null)

        DebugLogBuffer.entries.value.single().id shouldBe 1
    }

    "clear empties the buffer" {
        DebugLogBuffer.logWriter.log(Severity.Warn, "noise", "Sync", null)

        DebugLogBuffer.clear()

        DebugLogBuffer.entries.value shouldBe emptyList()
    }
})
