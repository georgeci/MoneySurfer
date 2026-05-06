package com.georgeci.moneysurfer.sync.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

@Single(binds = [BackgroundSyncScheduler::class])
class AndroidBackgroundSyncScheduler(
    context: Context,
) : BackgroundSyncScheduler {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = interval.inWholeMilliseconds,
            repeatIntervalTimeUnit = TimeUnit.MILLISECONDS,
        )
            .setConstraints(constraints.toWorkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_MIN_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setConstraints(constraints.toWorkConstraints())
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONESHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override suspend fun cancelAll() {
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_NAME)
        workManager.cancelUniqueWork(UNIQUE_ONESHOT_NAME)
    }

    private fun SyncConstraints.toWorkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                if (requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .setRequiresCharging(requireCharging)
            .setRequiresBatteryNotLow(requireBatteryNotLow)
            .build()

    private companion object {
        const val UNIQUE_PERIODIC_NAME = "sync.periodic"
        const val UNIQUE_ONESHOT_NAME = "sync.oneshot"
        const val BACKOFF_MIN_DELAY_SECONDS = 30L
    }
}
