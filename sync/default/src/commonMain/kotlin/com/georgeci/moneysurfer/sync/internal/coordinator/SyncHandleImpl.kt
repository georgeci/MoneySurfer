package com.georgeci.moneysurfer.sync.internal.coordinator

import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal class SyncHandleImpl(
    override val id: SyncRequestId,
    override val status: StateFlow<SyncHandleStatus>,
    override val steps: SharedFlow<SyncStep>,
    override val result: Deferred<SyncResult<SyncSummary>>,
    private val cancelAction: () -> Unit,
) : SyncHandle {
    override fun cancel() {
        cancelAction()
    }
}
