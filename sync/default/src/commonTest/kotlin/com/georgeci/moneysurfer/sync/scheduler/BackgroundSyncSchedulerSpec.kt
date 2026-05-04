package com.georgeci.moneysurfer.sync.scheduler

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the [BackgroundSyncScheduler] default-argument synthetics so the
 * interface contract is wired (no impl ships in `:sync`; this just guarantees
 * default constraints flow through correctly).
 */
class BackgroundSyncSchedulerSpec : StringSpec({

    class RecordingScheduler : BackgroundSyncScheduler {
        var lastPeriodicInterval: Duration? = null
        var lastPeriodicConstraints: SyncConstraints? = null
        var lastOneShotDelay: Duration? = null
        var lastOneShotConstraints: SyncConstraints? = null
        var cancelAllCount: Int = 0

        override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) {
            lastPeriodicInterval = interval
            lastPeriodicConstraints = constraints
        }

        override suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints) {
            lastOneShotDelay = delay
            lastOneShotConstraints = constraints
        }

        override suspend fun cancelAll() { cancelAllCount++ }
    }

    "schedulePeriodic uses default constraints when none supplied" {
        val scheduler = RecordingScheduler()
        scheduler.schedulePeriodic(15.minutes)

        scheduler.lastPeriodicInterval shouldBe 15.minutes
        scheduler.lastPeriodicConstraints shouldBe SyncConstraints()
    }

    "scheduleOneShot uses default constraints when none supplied" {
        val scheduler = RecordingScheduler()
        scheduler.scheduleOneShot(30.seconds)

        scheduler.lastOneShotDelay shouldBe 30.seconds
        scheduler.lastOneShotConstraints shouldBe SyncConstraints()
    }

    "cancelAll is callable" {
        val scheduler = RecordingScheduler()
        scheduler.cancelAll()
        scheduler.cancelAll()
        scheduler.cancelAllCount shouldBe 2
    }
})
