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
    afterSpec { Logger.setMinSeverity(originalMinSeverity) }

    "release builds mute everything below Warn" {
        configureLogging(isDebug = false)
        Logger.config.minSeverity shouldBe Severity.Warn
    }

    "debug builds keep verbose logging" {
        configureLogging(isDebug = true)
        Logger.config.minSeverity shouldBe Severity.Verbose
    }
})
