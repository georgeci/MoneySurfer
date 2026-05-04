package com.georgeci.moneysurfer.sync.coordinator

import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Handle returned from [SyncCoordinator.requestSync].
 *
 * Notes:
 * - [steps] is a hot `SharedFlow` configured with `replay = 1` and
 *   `BufferOverflow.DROP_OLDEST` (FAQ №20). Late subscribers see only the
 *   most recent step; the terminal step is always observable.
 * - Stopping `collect` does NOT cancel the sync. Only [cancel] does.
 * - [cancel] does not roll back already-performed work; outbox items that
 *   were already pushed remain pushed.
 */
interface SyncHandle {
    val id: SyncRequestId
    val status: StateFlow<SyncHandleStatus>
    val steps: SharedFlow<SyncStep>
    val result: Deferred<SyncResult<SyncSummary>>

    fun cancel()
}
