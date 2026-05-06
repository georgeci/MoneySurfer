package com.georgeci.moneysurfer.sync.scheduler

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import kotlin.time.Duration

/**
 * Phase 4 placeholder for iOS.
 *
 * Real implementation requires `BGTaskScheduler` via cinterop plus a Swift
 * bridge for the task handlers (BG handlers must be registered before
 * `application:didFinishLaunchingWithOptions:` returns). Until that is
 * wired up, the iOS app stays foreground-only — sync runs only when the
 * user opens the app (manual / app-start triggers in `SyncCoordinator`).
 *
 * Logs each call so we can verify nothing silently regresses to the stub
 * once a real implementation lands.
 *
 * Tracked under Phase 4+ follow-up; see sync.md §4.5 «iOS — BGTaskScheduler».
 */
@Single(binds = [BackgroundSyncScheduler::class])
class IosBackgroundSyncScheduler : BackgroundSyncScheduler {

    private val log = Logger.withTag("IosBackgroundSyncScheduler")

    override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) {
        log.i { "schedulePeriodic($interval, $constraints) — no-op until BGTaskScheduler is wired" }
    }

    override suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints) {
        log.i { "scheduleOneShot($delay, $constraints) — no-op until BGTaskScheduler is wired" }
    }

    override suspend fun cancelAll() {
        log.i { "cancelAll() — no-op until BGTaskScheduler is wired" }
    }
}
