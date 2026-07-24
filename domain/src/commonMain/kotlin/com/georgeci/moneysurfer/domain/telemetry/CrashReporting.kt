package com.georgeci.moneysurfer.domain.telemetry

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Wires [crashReporter] into the app: collection is enabled for release builds only, and
 * Kermit gains a [CrashReportingLogWriter] so Warn+ logs become breadcrumbs and logged
 * throwables become non-fatals — no call site has to know Crashlytics exists.
 *
 * When [userIds] is given (the session's Firebase uid, null when signed out) the
 * Crashlytics user id is kept in step with it for the lifetime of the process, so a
 * crash report can be tied back to the account that produced it. The uid is an opaque
 * Firebase identifier, not PII in the sense of issue #154 — unlike emails it never
 * reaches logcat, only the crash backend the account already lives in.
 *
 * Called once per process from `initKoin`, after Koin has started. Debug builds keep
 * collection off so local crashes never pollute the production Crashlytics dashboard.
 *
 * Idempotent: a second call is a no-op. Repeated `initKoin` calls — instrumented tests,
 * previews — therefore stack neither duplicate log writers nor duplicate uid collectors,
 * and the collector's scope is created at most once per process.
 */
fun installCrashReporting(
    crashReporter: CrashReporter,
    isDebug: Boolean,
    userIds: Flow<String?>? = null,
) {
    if (isInstalled) return
    isInstalled = true

    crashReporter.setCollectionEnabled(!isDebug)
    Logger.addLogWriter(CrashReportingLogWriter(crashReporter))

    if (userIds != null) {
        userBinding = bindCrashReportingUser(crashReporter, userIds, processScope)
    }
}

/**
 * Mirrors [userIds] onto the Crashlytics user id on [scope]. Exposed separately from
 * [installCrashReporting] so the binding can be driven by a caller-owned scope in tests;
 * production goes through [installCrashReporting], which owns a single process scope.
 */
fun bindCrashReportingUser(
    crashReporter: CrashReporter,
    userIds: Flow<String?>,
    scope: CoroutineScope,
): Job = userIds
    .onEach { uid -> crashReporter.setUserId(uid) }
    .launchIn(scope)

private var isInstalled = false
private var userBinding: Job? = null

// Process-scoped on purpose: the uid collector lives as long as the app does, and there
// is nothing above `initKoin` to own it. `installCrashReporting` is latched, so this is
// created at most once.
private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Test-only reset of the [installCrashReporting] latch and its uid collector. */
internal fun resetCrashReportingForTest() {
    userBinding?.cancel()
    userBinding = null
    isInstalled = false
}
