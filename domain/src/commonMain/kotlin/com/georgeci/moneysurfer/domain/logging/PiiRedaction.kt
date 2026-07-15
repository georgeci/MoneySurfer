package com.georgeci.moneysurfer.domain.logging

/**
 * PII redaction helpers for log messages. Emails and user identifiers (Firebase
 * UIDs / `UserId` values) are personal data and must never reach logcat or the
 * iOS unified log in cleartext — not even in release, where anyone with brief
 * physical access (`adb logcat`, Xcode Console) or a log / bug-report collector
 * could harvest the user's and their invitees' identities (issue #154).
 *
 * The masks keep just enough for a developer to correlate log lines without
 * exposing the underlying identity, and are applied at the call sites regardless
 * of build type — so a stray [co.touchlab.kermit.LogWriter] or a mistakenly
 * lowered min-severity can never leak the raw value. Release muting
 * ([configureLogging]) is the second layer.
 */

/** `john.doe@example.com` -> `j***@***.com`; blank / malformed -> a fixed marker. */
fun String?.redactEmail(): String {
    val email = this?.trim().orEmpty()
    if (email.isEmpty()) return BLANK_EMAIL
    val at = email.indexOf('@')
    if (at <= 0 || at == email.length - 1) return REDACTED_EMAIL
    val maskedLocal = "${email.first()}$MASK"
    val domain = email.substring(at + 1)
    val lastDot = domain.lastIndexOf('.')
    val maskedDomain = if (lastDot > 0) "$MASK${domain.substring(lastDot)}" else MASK
    return "$maskedLocal@$maskedDomain"
}

/**
 * Firebase UID / `UserId` mask: keeps a short prefix for cross-line correlation
 * and drops the rest. `abcd1234efgh` -> `abcd***`; blank or too-short values are
 * fully masked so nothing identifying survives.
 */
fun String?.redactUid(): String {
    val uid = this?.trim().orEmpty()
    if (uid.isEmpty()) return BLANK_UID
    return if (uid.length <= PREFIX_LEN) MASK else "${uid.take(PREFIX_LEN)}$MASK"
}

private const val MASK = "***"
private const val BLANK_EMAIL = "<blank-email>"
private const val REDACTED_EMAIL = "<redacted-email>"
private const val BLANK_UID = "<blank-uid>"
private const val PREFIX_LEN = 4
