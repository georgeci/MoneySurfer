package com.georgeci.moneysurfer.domain.telemetry

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Wires [crashReporter] into the app: collection is enabled for release builds only, and
 * Kermit gains a [CrashReportingLogWriter] so Warn+ logs become breadcrumbs and logged
 * throwables become non-fatals — no call site has to know Crashlytics exists.
 *
 * Called once per process from `initKoin`, after Koin has started. Debug builds keep
 * collection off so local crashes never pollute the production Crashlytics dashboard.
 *
 * Idempotent: a second call is a no-op, so repeated `initKoin` calls in instrumented
 * tests don't stack duplicate log writers.
 */
fun installCrashReporting(crashReporter: CrashReporter, isDebug: Boolean) {
    if (isInstalled) return
    isInstalled = true

    crashReporter.setCollectionEnabled(!isDebug)
    Logger.addLogWriter(CrashReportingLogWriter(crashReporter))
}

/**
 * Keeps the Crashlytics user id in step with [userIds] (the session's Firebase uid, null
 * when signed out) so a crash report can be tied back to the account that produced it.
 *
 * The uid is an opaque Firebase identifier, not PII in the sense of issue #154 — unlike
 * emails it never reaches logcat, only the crash backend the account already lives in.
 */
fun bindCrashReportingUser(
    crashReporter: CrashReporter,
    userIds: Flow<String?>,
    scope: CoroutineScope,
): Job = userIds
    .onEach { uid -> crashReporter.setUserId(uid) }
    .launchIn(scope)

private var isInstalled = false

/** Test-only reset of the [installCrashReporting] latch. */
internal fun resetCrashReportingForTest() {
    isInstalled = false
}
