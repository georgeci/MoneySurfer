package com.georgeci.moneysurfer.domain.model

/** Counts returned by a one-shot workspace sync. */
data class SyncReport(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val deleted: Int = 0,
) {
    operator fun plus(other: SyncReport): SyncReport = SyncReport(
        pushed = pushed + other.pushed,
        pulled = pulled + other.pulled,
        deleted = deleted + other.deleted,
    )
}
