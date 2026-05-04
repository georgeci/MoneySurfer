package com.georgeci.moneysurfer.sync.internal.coordinator

import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncRequestId

/**
 * Internal commands processed by the coordinator's actor loop.
 *
 * All cancellation flows go through commands rather than direct field
 * access — that is what eliminates the race on `currentRequest` /
 * `currentJob` (see SyncCoordinatorFAQ.md №1).
 */
internal sealed interface SyncCommand {
    data class Enqueue(val request: SyncRequest, val mode: SyncMode) : SyncCommand
    data class CancelHandle(val requestId: SyncRequestId) : SyncCommand
    data object CancelCurrent : SyncCommand
    data object CancelQueued : SyncCommand
    data object CancelAll : SyncCommand

    /** Sent by the worker job upon completion to trigger queue drain. */
    data object WorkerFinished : SyncCommand
}
