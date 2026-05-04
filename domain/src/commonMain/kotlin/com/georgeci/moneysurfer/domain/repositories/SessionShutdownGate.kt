package com.georgeci.moneysurfer.domain.repositories

/**
 * Tears down everything that should not survive across logout: in-flight
 * sync, queued sync, scheduled background runs.
 *
 * Lives in `:domain` so use cases (e.g. `LogoutUseCase`) can call it
 * without taking a direct dependency on `:sync`. Implementation lives in
 * `:data` and delegates to `SyncCoordinator.cancelAll()` plus
 * `BackgroundSyncScheduler.cancelAll()`.
 */
interface SessionShutdownGate {
    suspend fun shutdown()
}
