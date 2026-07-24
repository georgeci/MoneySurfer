package com.georgeci.moneysurfer.domain.telemetry

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Bridges Kermit into [CrashReporter]: every Warn-and-above line becomes a Crashlytics
 * breadcrumb, and every Error/Assert line that carries a [Throwable] is additionally
 * recorded as a non-fatal.
 *
 * The [Severity.Warn] floor is deliberate and matches the release min-severity set by
 * `configureLogging`: Verbose/Debug/Info are the severities that historically carried
 * PII (issue #154) and are dropped before their message lambda is even evaluated in
 * release builds, so nothing below Warn can reach a crash report in the first place.
 */
class CrashReportingLogWriter(
    private val crashReporter: CrashReporter,
) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= Severity.Warn

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        crashReporter.log("${severity.name.uppercase()}/${tag.ifBlank { "NoTag" }}: $message")

        if (severity >= Severity.Error && throwable != null) {
            crashReporter.record(throwable)
        }
    }
}
