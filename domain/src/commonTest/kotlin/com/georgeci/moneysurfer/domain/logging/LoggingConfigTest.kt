package com.georgeci.moneysurfer.domain.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LoggingConfigTest : StringSpec({

    // Global Kermit config is process-wide mutable state; restore the permissive
    // default so this spec can't starve logging in specs that run after it.
    afterSpec { Logger.setMinSeverity(Severity.Verbose) }

    "release builds mute everything below Warn" {
        configureLogging(isDebug = false)
        Logger.config.minSeverity shouldBe Severity.Warn
    }

    "debug builds keep verbose logging" {
        configureLogging(isDebug = true)
        Logger.config.minSeverity shouldBe Severity.Verbose
    }
})
