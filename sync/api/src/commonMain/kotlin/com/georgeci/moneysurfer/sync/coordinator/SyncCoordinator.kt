package com.georgeci.moneysurfer.sync.coordinator

import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncState
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point for sync.
 *
 * UI and feature code never talk to Firestore directly — they go through
 * [requestSync]. The coordinator merges concurrent requests, runs at most
 * one physical sync at a time, and emits progress through [SyncHandle].
 *
 * - [state] reflects only live activity (Idle / Queued / Running). Terminal
 *   results live in [lastOutcome].
 * - Lives in `:sync/runtime/ApplicationScope`, not in any `viewModelScope`.
 *
 * See SyncCoordinator.md and SyncCoordinatorFAQ.md (especially №10, №19) for
 * the full design rationale.
 */
interface SyncCoordinator {
    val state: StateFlow<SyncState>
    val lastOutcome: StateFlow<LastSyncOutcome>

    fun requestSync(
        reason: SyncReason,
        mode: SyncMode = SyncMode.Enqueue,
    ): SyncHandle

    /** Cancels the currently running sync, leaves queued requests in place. */
    fun cancelCurrent()

    /** Cancels all queued requests, leaves the running sync in place. */
    fun cancelAllQueued()

    /** Atomically cancels current + queued. */
    fun cancelAll()
}
