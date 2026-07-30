package com.georgeci.moneysurfer.domain.debug

import arrow.core.Either
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.formatter.CurrencyDefaults
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Reads the pinned workspace, plans a batch against it and writes it. See [DebugDataPrefiller] for
 * what this is for and why the ordinary repositories are the write path.
 */
@Single(binds = [DebugDataPrefiller::class])
internal class DebugDataPrefillerImpl(
    private val session: SessionPointers,
    private val workspaceRepository: WorkspaceRepository,
    private val writer: DemoDataWriter,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val syncCoordinator: SyncCoordinator,
) : DebugDataPrefiller {

    private val log = Logger.withTag(TAG)

    override suspend fun prefill(): Either<DebugPrefillError, DebugPrefillReport> = either {
        val workspaceId = session.currentWorkspaceId.first()
            ?: raise(DebugPrefillError.NoWorkspace)
        val now = getCurrentTime()
        log.i { "[start] wid=${workspaceId.value}" }

        val report = Either
            .catch {
                // A workspace with no base currency is not a state the app produces; falling back
                // to the locale's keeps the run going rather than refusing over a cosmetic field.
                val currency = workspaceRepository.getById(workspaceId)?.baseCurrency
                    ?: CurrencyDefaults.systemDefault()
                val start = writer.snapshot(workspaceId, currency, now)
                writer.write(buildDemoDataPlan(start.snapshot, now), start.categoriesSeeded)
            }
            .onLeft { log.e(it) { "[abort] write failed wid=${workspaceId.value}" } }
            .mapLeft { DebugPrefillError.WriteFailed(it) }
            .bind()

        log.i { "[done] $report" }
        pushIfSignedIn()
        report
    }

    /**
     * The rows are already on the outbox; this only saves the tester waiting for the next tick.
     * Skipped for a guest session, which has no Firebase uid and therefore nowhere to drain to —
     * demo data must never reach Firestore (`docs/architecture/sync.md` §2.11).
     */
    private suspend fun pushIfSignedIn() {
        if (session.currentFirebaseUid.first() == null) {
            log.i { "[sync] skipped — local/demo session, rows stay on the device" }
            return
        }
        syncCoordinator.requestSync(SyncReason.LOCAL_CHANGE)
    }

    private companion object {
        const val TAG = "DebugPrefill"
    }
}
