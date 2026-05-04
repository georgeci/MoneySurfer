package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import com.georgeci.moneysurfer.sync.internal.SessionShutdownGateImpl
import com.georgeci.moneysurfer.sync.scheduler.BackgroundSyncScheduler
import com.georgeci.moneysurfer.sync.scheduler.SyncConstraints
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration

private class RecordingCoordinator : SyncCoordinator {
    var cancelAllCount: Int = 0
    override val state: StateFlow<SyncState> = MutableStateFlow<SyncState>(SyncState.Idle).asStateFlow()
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow<LastSyncOutcome>(
        LastSyncOutcome.None,
    ).asStateFlow()
    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle = error("unused")
    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() { cancelAllCount++ }
}

private class RecordingScheduler : BackgroundSyncScheduler {
    var cancelAllCount: Int = 0
    override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) = Unit
    override suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints) = Unit
    override suspend fun cancelAll() { cancelAllCount++ }
}

class SessionShutdownGateImplSpec : StringSpec({

    "shutdown cancels coordinator and scheduler" {
        runTest {
            val coordinator = RecordingCoordinator()
            val scheduler = RecordingScheduler()
            val gate = SessionShutdownGateImpl(coordinator, scheduler)

            gate.shutdown()

            coordinator.cancelAllCount shouldBe 1
            scheduler.cancelAllCount shouldBe 1
        }
    }

    "shutdown is idempotent — repeated calls keep cancelling" {
        runTest {
            val coordinator = RecordingCoordinator()
            val scheduler = RecordingScheduler()
            val gate = SessionShutdownGateImpl(coordinator, scheduler)

            gate.shutdown()
            gate.shutdown()

            coordinator.cancelAllCount shouldBe 2
            scheduler.cancelAllCount shouldBe 2
        }
    }
})
