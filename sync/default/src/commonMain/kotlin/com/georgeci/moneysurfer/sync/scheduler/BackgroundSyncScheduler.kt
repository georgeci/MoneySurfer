package com.georgeci.moneysurfer.sync.scheduler

import kotlin.time.Duration

/**
 * Platform-agnostic API for scheduling sync runs outside the UI lifecycle.
 *
 * Implementations live in `:data` per platform:
 * - Android — `WorkManager` (`PeriodicWorkRequest` / `OneTimeWorkRequest`).
 * - iOS — `BGTaskScheduler` (`BGAppRefreshTask` / `BGProcessingTask`).
 * - Desktop — coroutine `delay`-loop on `ApplicationScope`.
 *
 * The scheduler is the **executor**, not the policy: it does not decide
 * *when* to schedule, callers do (login, app start, manual force).
 *
 * See sync.md §4.5.
 */
interface BackgroundSyncScheduler {

    /**
     * Schedule a recurring background sync. Replacing an existing periodic
     * schedule is implementation-defined: Android `KEEP` policy ignores the
     * second call, desktop / iOS implementations cancel the previous loop.
     */
    suspend fun schedulePeriodic(
        interval: Duration,
        constraints: SyncConstraints = SyncConstraints(),
    )

    /**
     * Schedule a single sync run after [delay]. Useful for retrying a
     * sync that failed with [com.georgeci.moneysurfer.sync.api.SyncError]
     * where `isRetryable = true`.
     */
    suspend fun scheduleOneShot(
        delay: Duration,
        constraints: SyncConstraints = SyncConstraints(),
    )

    /** Cancel both periodic and one-shot schedules — used at logout. */
    suspend fun cancelAll()
}

/**
 * Platform constraints that the scheduler honours where supported.
 *
 * Desktop ignores them (no native battery / network APIs). iOS supports
 * `requireUnmeteredNetwork` / `requireCharging` only via `BGProcessingTask`.
 */
data class SyncConstraints(
    val requireUnmeteredNetwork: Boolean = false,
    val requireCharging: Boolean = false,
    val requireBatteryNotLow: Boolean = true,
)
