package com.georgeci.moneysurfer.sync.api

import kotlin.time.Instant

/**
 * Persistent "last sync outcome" — for UI badges like "last synced N min ago".
 * Separate from [SyncState] (live activity).
 * See SyncCoordinatorFAQ.md №10.
 */
sealed interface LastSyncOutcome {
    data object None : LastSyncOutcome
    data class Success(val summary: SyncSummary, val at: Instant) : LastSyncOutcome
    data class Failed(val error: SyncError, val at: Instant) : LastSyncOutcome
    data class Cancelled(val at: Instant) : LastSyncOutcome
}
