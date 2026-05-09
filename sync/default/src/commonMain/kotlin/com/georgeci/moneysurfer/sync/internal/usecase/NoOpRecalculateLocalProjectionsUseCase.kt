package com.georgeci.moneysurfer.sync.internal.usecase

import arrow.core.right
import com.georgeci.moneysurfer.domain.sync.ProjectionScope
import com.georgeci.moneysurfer.domain.sync.ProjectionSummary
import com.georgeci.moneysurfer.domain.sync.RecalculateLocalProjectionsUseCase
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult
import org.koin.core.annotation.Single

/**
 * Placeholder kept until the real projection subsystem is implemented (Phase 5+).
 *
 * Real account-balance / aggregate recalculation depends on the project's
 * balance computation rules (signed amounts, INITIAL_BALANCE handling,
 * multi-currency). It will be implemented as a dedicated subsystem in Phase 5+
 * alongside projections / derived views work, then wired up here as the
 * no-op replacement.
 *
 * The contract is preserved: the coordinator only invokes this step when
 * `pullSummary.downloadedCount > 0` (FAQ №7), so a NoOp here costs nothing
 * on idle cycles.
 */
@Single(binds = [RecalculateLocalProjectionsUseCase::class])
internal class NoOpRecalculateLocalProjectionsUseCase : RecalculateLocalProjectionsUseCase {
    override suspend fun invoke(
        scope: ProjectionScope,
        cancelToken: SyncCancelToken,
    ): SyncResult<ProjectionSummary> = ProjectionSummary(recalculatedCount = 0).right()
}
