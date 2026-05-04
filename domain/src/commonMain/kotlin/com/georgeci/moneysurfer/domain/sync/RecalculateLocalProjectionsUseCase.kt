package com.georgeci.moneysurfer.domain.sync

import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult

/**
 * Recalculates derived stored values (account balances, aggregates) for the
 * given [ProjectionScope].
 *
 * Called both from the sync pipeline (after pull writes) and from local
 * mutation paths (after dual-write to outbox). The same use case is reused
 * to avoid drift between paths. See SyncCoordinatorFAQ.md №6, №7.
 */
interface RecalculateLocalProjectionsUseCase {
    suspend operator fun invoke(
        scope: ProjectionScope,
        cancelToken: SyncCancelToken,
    ): SyncResult<ProjectionSummary>
}
