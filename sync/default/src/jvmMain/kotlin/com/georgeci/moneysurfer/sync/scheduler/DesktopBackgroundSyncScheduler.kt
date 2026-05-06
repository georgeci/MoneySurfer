package com.georgeci.moneysurfer.sync.scheduler

import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.invoke
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.time.Duration

/**
 * Coroutine-driven scheduler for the desktop (JVM) target. There is no
 * native battery / network observability on plain JVM, so [SyncConstraints]
 * are accepted but ignored.
 *
 * Lives on [ApplicationScope] — survives window minimisation, dies with
 * the JVM process.
 */
@Single(binds = [BackgroundSyncScheduler::class])
class DesktopBackgroundSyncScheduler(
    private val appScope: ApplicationScope,
    private val coordinator: SyncCoordinator,
) : BackgroundSyncScheduler {

    private val mutex = Mutex()
    private var periodicJob: Job? = null
    private val oneShotJobs = mutableListOf<Job>()

    override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) {
        mutex.withLock {
            periodicJob?.cancel()
            periodicJob = appScope.launch {
                while (isActive) {
                    delay(interval)
                    coordinator.requestSync(SyncReason.BACKGROUND).result.await()
                }
            }
        }
    }

    override suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints) {
        mutex.withLock {
            val job = appScope.launch {
                delay(delay)
                coordinator.requestSync(SyncReason.BACKGROUND).result.await()
            }
            oneShotJobs += job
            job.invokeOnCompletion {
                // Mutex serialises adds and removes; race is benign because
                // ArrayList tolerates concurrent reads but not modifications.
                appScope.launch { mutex.withLock { oneShotJobs.remove(job) } }
            }
        }
    }

    override suspend fun cancelAll() {
        mutex.withLock {
            periodicJob?.cancel()
            periodicJob = null
            oneShotJobs.forEach { it.cancel() }
            oneShotJobs.clear()
        }
    }
}
