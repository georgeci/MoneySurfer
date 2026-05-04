package com.georgeci.moneysurfer.domain.sync

import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope

/**
 * Pulls remote changes since the last cursor, applies them locally, and resolves
 * conflicts inline (LWW by default).
 *
 * Pull-applied writes MUST bypass the outbox to avoid push echo.
 * See SyncCoordinatorFAQ.md №5, §4.3.
 */
interface PullRemoteChangesUseCase {
    suspend operator fun invoke(
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<PullSummary>
}

data class PullProgress(
    val collection: String,
    val current: Int,
    val total: Int,
)

data class PullSummary(
    val downloadedCount: Int,
    val conflictCount: Int,
)
