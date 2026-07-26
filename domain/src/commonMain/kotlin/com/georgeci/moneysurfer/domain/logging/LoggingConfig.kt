package com.georgeci.moneysurfer.domain.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Global Kermit severity gate, called once from `initKoin` at every host's
 * startup.
 *
 * Release builds run at [Severity.Warn]: Verbose/Debug/Info calls — which
 * historically carried invitee emails and Firebase UIDs — are dropped before
 * their message lambda is even evaluated (Kermit checks min-severity first), so
 * the PII string is never constructed, let alone written to logcat / the iOS
 * unified log (issue #154). Debug builds keep [Severity.Verbose] for full
 * diagnostics.
 *
 * This is defence-in-depth with [redactEmail] / [redactUid], which run at the
 * call sites regardless of build type so the Warn/Error lines that do survive
 * here still never carry raw PII.
 *
 * Debug builds additionally install [DebugLogBuffer], so every host — not just
 * the one that happens to have a console attached — can show its last Warn/Error
 * lines in the Settings debug panel.
 */
fun configureLogging(isDebug: Boolean) {
    Logger.setMinSeverity(if (isDebug) Severity.Verbose else Severity.Warn)
    if (isDebug) {
        DebugLogBuffer.install()
    }
}
