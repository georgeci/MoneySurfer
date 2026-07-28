package com.georgeci.moneysurfer.domain.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LoggingConfigTest : StringSpec({

    // Global Kermit config is process-wide mutable state; capture whatever the
    // min-severity was before this spec ran and restore exactly that, so we don't
    // clobber a value another spec relies on.
    val originalMinSeverity = Logger.config.minSeverity

    // The writer list needs the same treatment, and `addLogWriter` has no counterpart
    // that removes one. `configureLogging(isDebug = true)` installs DebugLogBuffer, and
    // leaving it attached would funnel every later spec's Warn/Error line into a
    // process-wide buffer that DebugLogBufferTest asserts the exact contents of.
    val originalLogWriters = Logger.config.logWriterList

    afterSpec {
        Logger.setMinSeverity(originalMinSeverity)
        Logger.mutableConfig.logWriterList = originalLogWriters
        DebugLogBuffer.resetInstallLatchForTest()
        DebugLogBuffer.clear()
    }

    "release builds mute everything below Warn" {
        configureLogging(isDebug = false)
        Logger.config.minSeverity shouldBe Severity.Warn
    }

    "debug builds keep verbose logging" {
        configureLogging(isDebug = true)
        Logger.config.minSeverity shouldBe Severity.Verbose
    }
})
