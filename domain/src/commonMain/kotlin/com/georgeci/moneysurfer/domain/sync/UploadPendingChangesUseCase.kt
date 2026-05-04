package com.georgeci.moneysurfer.domain.sync

import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope

/**
 * Pushes pending mutations from the outbox to the remote backend.
 *
 * Implementations must classify SDK exceptions into [com.georgeci.moneysurfer.sync.api.SyncError]
 * and return them via `Either.Left` — never throw typed errors out of here.
 * See SyncCoordinatorFAQ.md №11, §4.2.
 */
interface UploadPendingChangesUseCase {
    suspend operator fun invoke(
        scope: SyncScope,
        onProgress: suspend (UploadProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<UploadSummary>
}

data class UploadProgress(
    val entityType: String,
    val current: Int,
    val total: Int,
)

data class UploadSummary(val uploadedCount: Int)
