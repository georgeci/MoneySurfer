package com.georgeci.moneysurfer.sync.internal.coordinator

import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.api.toScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One sync request created by [com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator.requestSync].
 *
 * Multiple requests can be merged into one [MergedSyncRequest], but each
 * request keeps its own [steps] / [status] / [result] / [cancelToken] so the
 * caller's `SyncHandle` reflects only its own lifecycle.
 */
internal class SyncRequest(
    val id: SyncRequestId,
    val reason: SyncReason,
    val scope: SyncScope = reason.toScope(),
    val cancelToken: SyncCancelToken = SimpleCancelToken(),
    val steps: MutableSharedFlow<SyncStep> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ),
    val status: MutableStateFlow<SyncHandleStatus> = MutableStateFlow(SyncHandleStatus.Queued),
    val result: CompletableDeferred<SyncResult<SyncSummary>> = CompletableDeferred(),
)
