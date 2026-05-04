package com.georgeci.moneysurfer.sync.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background entry point on Android. Resolves [SyncCoordinator] from Koin
 * and waits for the run to finish.
 *
 * Mapping of [com.georgeci.moneysurfer.sync.api.SyncResult] onto [Result]:
 * - success → [Result.success]
 * - retryable error → [Result.retry] (WorkManager backoff handles delay)
 * - non-retryable error → [Result.failure]
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val coordinator: SyncCoordinator by inject()

    override suspend fun doWork(): Result {
        val handle = coordinator.requestSync(reason = SyncReason.BACKGROUND)
        val outcome = handle.result.await()
        return outcome.fold(
            ifLeft = { error ->
                if (error.isRetryable) Result.retry() else Result.failure()
            },
            ifRight = { Result.success() },
        )
    }
}
