package com.georgeci.moneysurfer.sync.internal.coordinator

import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.mergeScope

/**
 * Multiple [SyncRequest]s collapsed into a single physical sync run.
 *
 * Each child keeps its own state (steps / status / result / cancelToken),
 * so callers see only their own lifecycle. The merged request carries
 * the union of reasons and the widest scope.
 */
internal data class MergedSyncRequest(
    val id: SyncRequestId,
    val reasons: Set<SyncReason>,
    val scope: SyncScope,
    val childRequests: List<SyncRequest>,
) {
    fun merge(request: SyncRequest): MergedSyncRequest = copy(
        reasons = reasons + request.reason,
        scope = mergeScope(scope, request.scope),
        childRequests = childRequests + request,
    )

    companion object {
        fun from(request: SyncRequest): MergedSyncRequest = MergedSyncRequest(
            id = request.id,
            reasons = setOf(request.reason),
            scope = request.scope,
            childRequests = listOf(request),
        )
    }
}
