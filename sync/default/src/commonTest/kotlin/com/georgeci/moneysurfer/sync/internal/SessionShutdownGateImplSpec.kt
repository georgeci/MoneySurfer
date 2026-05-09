package com.georgeci.moneysurfer.sync.internal

import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import com.georgeci.moneysurfer.sync.scheduler.BackgroundSyncScheduler
import com.georgeci.moneysurfer.sync.scheduler.SyncConstraints
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration

private class RecordingCoordinator(private val log: MutableList<String>) : SyncCoordinator {
    override val state: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle)
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow(LastSyncOutcome.None)
    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle = error("not used")
    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() { log += "coordinator.cancelAll" }
}

private class RecordingScheduler(private val log: MutableList<String>) : BackgroundSyncScheduler {
    override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) = Unit
    override suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints) = Unit
    override suspend fun cancelAll() { log += "scheduler.cancelAll" }
}

class SessionShutdownGateImplSpec : StringSpec({

    "shutdown cancels coordinator first, then scheduler" {
        runTest {
            val log = mutableListOf<String>()
            val gate = SessionShutdownGateImpl(
                coordinator = RecordingCoordinator(log),
                scheduler = RecordingScheduler(log),
            )

            gate.shutdown()

            log shouldContainExactly listOf("coordinator.cancelAll", "scheduler.cancelAll")
        }
    }

    "shutdown is safe to call repeatedly (idempotent)" {
        runTest {
            val log = mutableListOf<String>()
            val gate = SessionShutdownGateImpl(
                coordinator = RecordingCoordinator(log),
                scheduler = RecordingScheduler(log),
            )

            gate.shutdown()
            gate.shutdown()

            log shouldContainExactly listOf(
                "coordinator.cancelAll",
                "scheduler.cancelAll",
                "coordinator.cancelAll",
                "scheduler.cancelAll",
            )
        }
    }
})
