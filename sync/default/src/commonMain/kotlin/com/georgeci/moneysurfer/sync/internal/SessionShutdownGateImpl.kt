package com.georgeci.moneysurfer.sync.internal

import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.scheduler.BackgroundSyncScheduler
import org.koin.core.annotation.Single

/**
 * Order matters:
 * 1. Cancel queued / running sync first so no in-flight push/pull races
 *    with the upcoming local data wipe.
 * 2. Cancel background schedules so they don't fire after wipe and re-upload
 *    nothing or hit `AuthRequired` because the session is gone.
 *
 * Both [SyncCoordinator.cancelAll] and [BackgroundSyncScheduler.cancelAll]
 * are idempotent — safe to call multiple times.
 */
@Single(binds = [SessionShutdownGate::class])
class SessionShutdownGateImpl(
    private val coordinator: SyncCoordinator,
    private val scheduler: BackgroundSyncScheduler,
) : SessionShutdownGate {

    override suspend fun shutdown() {
        coordinator.cancelAll()
        scheduler.cancelAll()
    }
}
