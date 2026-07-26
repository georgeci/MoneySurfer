package com.georgeci.moneysurfer.domain.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One Warn-or-above line as the debug log panel renders it. */
data class DebugLogEntry(
    val severity: Severity,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)

/**
 * In-memory ring buffer of the last [MaxEntries] Warn/Error lines, read by the debug log panel in
 * Settings.
 *
 * Exists because only iOS had a way to see these without a cable: logcat and the desktop console
 * are not reachable from a device a tester is holding. The buffer is host-agnostic, so Android,
 * iOS and desktop — online and offline builds alike — all get the same panel from the single
 * install in [configureLogging].
 *
 * Debug builds only, installed once per process. Nothing here is a reporting path: Warn+ lines
 * reach Crashlytics through
 * [CrashReportingLogWriter][com.georgeci.moneysurfer.domain.telemetry.CrashReportingLogWriter]
 * regardless, and this buffer only decides what a tester can read on the device. The
 * [Severity.Warn] floor matches that writer's, and is the same floor release builds apply
 * globally — the severities that historically carried PII (issue #154) never reach here.
 */
object DebugLogBuffer {

    private const val MaxEntries = 100

    private var isInstalled = false
    private val mutableEntries = MutableStateFlow<List<DebugLogEntry>>(emptyList())

    /** Newest first. Empty in release builds, where [install] is never called. */
    val entries: StateFlow<List<DebugLogEntry>> = mutableEntries

    /**
     * Idempotent: a second call is a no-op, so repeated `initKoin` calls — instrumented tests,
     * previews — do not stack duplicate writers.
     */
    fun install() {
        if (isInstalled) return
        isInstalled = true
        Logger.addLogWriter(logWriter)
    }

    fun clear() {
        mutableEntries.value = emptyList()
    }

    /**
     * Internal rather than private so the buffer can be exercised without touching Kermit's global
     * writer list, which no test can undo.
     */
    internal val logWriter: LogWriter = object : LogWriter() {

        override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= Severity.Warn

        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            // `update` rather than `value =`: log lines arrive from whichever thread the failing
            // coroutine ran on (sync uploads on Dispatchers.Default, most of all), and a
            // read-modify-write on `value` drops entries when two of them land at once.
            mutableEntries.update { current ->
                listOf(
                    DebugLogEntry(
                        severity = severity,
                        tag = tag,
                        message = message,
                        throwable = throwable,
                    ),
                ) + current.take(MaxEntries - 1)
            }
        }
    }
}
