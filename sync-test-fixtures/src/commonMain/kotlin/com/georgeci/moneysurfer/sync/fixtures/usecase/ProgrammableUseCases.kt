package com.georgeci.moneysurfer.sync.fixtures.usecase

import arrow.core.right
import com.georgeci.moneysurfer.domain.sync.ProjectionScope
import com.georgeci.moneysurfer.domain.sync.ProjectionSummary
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullRemoteChangesUseCase
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.RecalculateLocalProjectionsUseCase
import com.georgeci.moneysurfer.domain.sync.UploadPendingChangesUseCase
import com.georgeci.moneysurfer.domain.sync.UploadProgress
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope

/**
 * Use-case fakes whose behaviour is supplied as a suspend lambda.
 *
 * Default behaviour returns an empty success result, mirroring the no-op
 * production path. Tests pass a custom lambda to drive progress emits,
 * failures, throws, or to gate the call with a [kotlinx.coroutines.CompletableDeferred].
 */
class ProgrammableUploadUseCase(
    private val behavior: suspend (
        SyncScope,
        suspend (UploadProgress) -> Unit,
        SyncCancelToken,
    ) -> SyncResult<UploadSummary> =
        { _, _, _ -> UploadSummary(uploadedCount = 0).right() },
) : UploadPendingChangesUseCase {
    override suspend fun invoke(
        scope: SyncScope,
        onProgress: suspend (UploadProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<UploadSummary> = behavior(scope, onProgress, cancelToken)
}

class ProgrammablePullUseCase(
    private val behavior: suspend (
        SyncScope,
        suspend (PullProgress) -> Unit,
        SyncCancelToken,
    ) -> SyncResult<PullSummary> =
        { _, _, _ -> PullSummary(downloadedCount = 0, conflictCount = 0).right() },
) : PullRemoteChangesUseCase {
    override suspend fun invoke(
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<PullSummary> = behavior(scope, onProgress, cancelToken)
}

class ProgrammableRecalculateUseCase(
    private val behavior: suspend (ProjectionScope, SyncCancelToken) -> SyncResult<ProjectionSummary> =
        { _, _ -> ProjectionSummary(recalculatedCount = 0).right() },
) : RecalculateLocalProjectionsUseCase {
    override suspend fun invoke(
        scope: ProjectionScope,
        cancelToken: SyncCancelToken,
    ): SyncResult<ProjectionSummary> = behavior(scope, cancelToken)
}
