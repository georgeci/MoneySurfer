package com.georgeci.moneysurfer.sync.noop

import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import arrow.core.right
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

/**
 * Offline build has no remote sync. The coordinator stays permanently Idle,
 * and any request returns a handle that completes immediately with an empty
 * summary so callers awaiting the result aren't left hanging.
 */
@Single(binds = [SyncCoordinator::class])
class NoOpSyncCoordinator : SyncCoordinator {
    override val state: StateFlow<SyncState> =
        MutableStateFlow(SyncState.Idle).asStateFlow()

    override val lastOutcome: StateFlow<LastSyncOutcome> =
        MutableStateFlow(LastSyncOutcome.None).asStateFlow()

    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle =
        NoOpSyncHandle()

    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() = Unit
}

private class NoOpSyncHandle : SyncHandle {
    override val id: SyncRequestId = SyncRequestId.uuid()

    private val emptySummary = SyncSummary()

    override val status: StateFlow<SyncHandleStatus> =
        MutableStateFlow(SyncHandleStatus.Completed(emptySummary)).asStateFlow()

    override val steps: SharedFlow<SyncStep> =
        MutableSharedFlow<SyncStep>(replay = 1).also {
            it.tryEmit(SyncStep.Completed(emptySummary))
        }.asSharedFlow()

    override val result: Deferred<SyncResult<SyncSummary>> =
        CompletableDeferred<SyncResult<SyncSummary>>().apply {
            complete(emptySummary.right())
        }

    override fun cancel() = Unit
}
