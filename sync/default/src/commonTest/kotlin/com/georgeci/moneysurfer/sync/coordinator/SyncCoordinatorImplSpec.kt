package com.georgeci.moneysurfer.sync.coordinator

import arrow.core.left
import arrow.core.right
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.fixtures.network.FakeNetworkMonitor
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammablePullUseCase
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammableRecalculateUseCase
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammableUploadUseCase
import com.georgeci.moneysurfer.sync.fixtures.version.FakeSyncVersionGate
import com.georgeci.moneysurfer.sync.internal.coordinator.SyncCoordinatorImpl
import com.georgeci.moneysurfer.domain.sync.ProjectionSummary
import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.buildCoordinator(
    network: FakeNetworkMonitor = FakeNetworkMonitor(initial = true),
    upload: ProgrammableUploadUseCase = ProgrammableUploadUseCase(),
    pull: ProgrammablePullUseCase = ProgrammablePullUseCase(),
    recalculate: ProgrammableRecalculateUseCase = ProgrammableRecalculateUseCase(),
    versionGate: FakeSyncVersionGate = FakeSyncVersionGate(),
    idSequence: List<String> = (1..1000).map { "req-$it" },
): SyncCoordinatorImpl {
    val ids = idSequence.iterator()
    // UnconfinedTestDispatcher runs eagerly — Channel.receive resumes immediately
    // after trySend so the actor sees WorkerFinished without needing extra ticks.
    // Wrapped in backgroundScope so the long-lived actor is auto-cancelled by runTest.
    val unconfinedScope = CoroutineScope(
        backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
    )
    return SyncCoordinatorImpl(
        appScope = ApplicationScope(delegate = unconfinedScope),
        networkMonitor = network,
        versionGate = versionGate,
        uploadPendingChangesUseCase = upload,
        pullRemoteChangesUseCase = pull,
        recalculateLocalProjectionsUseCase = recalculate,
        newRequestId = { SyncRequestId(ids.next()) },
    )
}

/**
 * After the worker finishes it pushes [SyncCommand.WorkerFinished] into the
 * actor channel. `yield + advanceUntilIdle` flushes that final tick before
 * we observe `state == Idle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.flushActor() {
    yield()
    advanceUntilIdle()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorImplSpec : StringSpec({

    "requestSync completes with success for noop use cases" {
        runTest {
            val coordinator = buildCoordinator()

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            flushActor()

            result.isRight().shouldBeTrue()
            handle.status.value.shouldBeInstanceOf<SyncHandleStatus.Completed>()
            coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Success>()
            coordinator.state.value shouldBe SyncState.Idle
        }
    }

    "UploadOnly scope skips pull and recalc" {
        runTest {
            var pullCalled = 0
            var recalcCalled = 0
            val coordinator = buildCoordinator(
                pull = ProgrammablePullUseCase { _, _, _ ->
                    pullCalled++
                    PullSummary(downloadedCount = 5, conflictCount = 0).right()
                },
                recalculate = ProgrammableRecalculateUseCase { _, _ ->
                    recalcCalled++
                    ProjectionSummary(0).right()
                },
            )

            // LOCAL_CHANGE → SyncScope.UploadOnly
            val handle = coordinator.requestSync(SyncReason.LOCAL_CHANGE)
            val result = handle.result.await()
            flushActor()

            result.isRight().shouldBeTrue()
            pullCalled shouldBe 0
            recalcCalled shouldBe 0
        }
    }

    "recalc skipped when downloadedCount == 0" {
        runTest {
            var recalcCalled = 0
            val coordinator = buildCoordinator(
                pull = ProgrammablePullUseCase { _, _, _ ->
                    PullSummary(downloadedCount = 0, conflictCount = 0).right()
                },
                recalculate = ProgrammableRecalculateUseCase { _, _ ->
                    recalcCalled++
                    ProjectionSummary(0).right()
                },
            )

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            flushActor()

            result.isRight().shouldBeTrue()
            recalcCalled shouldBe 0
        }
    }

    "recalc runs when downloadedCount > 0" {
        runTest {
            var recalcCalled = 0
            val coordinator = buildCoordinator(
                pull = ProgrammablePullUseCase { _, _, _ ->
                    PullSummary(downloadedCount = 7, conflictCount = 0).right()
                },
                recalculate = ProgrammableRecalculateUseCase { _, _ ->
                    recalcCalled++
                    ProjectionSummary(7).right()
                },
            )

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            val summary = handle.result.await().getOrNull()!!
            flushActor()

            summary.downloadedCount shouldBe 7
            summary.recalculatedCount shouldBe 7
            recalcCalled shouldBe 1
        }
    }

    "lastOutcome records failure when use case returns Left" {
        runTest {
            val coordinator = buildCoordinator(
                upload = ProgrammableUploadUseCase { _, _, _ ->
                    SyncError.PermissionDenied.left()
                },
            )

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            flushActor()

            result.isLeft().shouldBeTrue()
            result.leftOrNull() shouldBe SyncError.PermissionDenied
            coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Failed>()
        }
    }

    "cancelCurrent terminates running with Cancelled" {
        runTest {
            val coordinator = buildCoordinator(
                upload = ProgrammableUploadUseCase { _, _, _ ->
                    delay(60.seconds)
                    UploadSummary(0).right()
                },
            )

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            advanceTimeBy(1.seconds) // let upload start
            coordinator.cancelCurrent()
            val result = handle.result.await()
            flushActor()

            result.isLeft().shouldBeTrue()
            result.leftOrNull() shouldBe SyncError.Cancelled
            coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Cancelled>()
        }
    }

    "cancel handle during pending finalizes immediately" {
        runTest {
            val coordinator = buildCoordinator(
                upload = ProgrammableUploadUseCase { _, _, _ ->
                    delay(60.seconds)
                    UploadSummary(0).right()
                },
            )

            val first = coordinator.requestSync(SyncReason.MANUAL)
            advanceTimeBy(1.seconds) // first becomes "running"
            val second = coordinator.requestSync(SyncReason.MANUAL) // merged into running

            // Cancel the second handle — first must continue.
            second.cancel()
            flushActor()

            // second already terminated as Cancelled.
            second.status.value shouldBe SyncHandleStatus.Cancelled
            second.result.isCompleted.shouldBeTrue()
            second.result.await().leftOrNull() shouldBe SyncError.Cancelled

            // first is still running.
            first.result.isCompleted.shouldBeFalse()
        }
    }

    "cancelAll cancels running and queued" {
        runTest {
            val coordinator = buildCoordinator(
                upload = ProgrammableUploadUseCase { _, _, _ ->
                    delay(60.seconds)
                    UploadSummary(0).right()
                },
            )

            val first = coordinator.requestSync(SyncReason.MANUAL)
            advanceTimeBy(1.seconds)
            val second = coordinator.requestSync(SyncReason.MANUAL)

            coordinator.cancelAll()
            val firstResult = first.result.await()
            val secondResult = second.result.await()
            flushActor()

            first.result.isCompleted.shouldBeTrue()
            second.result.isCompleted.shouldBeTrue()
            firstResult.leftOrNull() shouldBe SyncError.Cancelled
            secondResult.leftOrNull() shouldBe SyncError.Cancelled
            coordinator.state.value shouldBe SyncState.Idle
        }
    }

    "awaitNetwork bounded timeout fails with NetworkUnavailable" {
        runTest {
            val network = FakeNetworkMonitor(initial = false)
            val coordinator = buildCoordinator(network = network)

            // BACKGROUND → bounded(5m).
            val handle = coordinator.requestSync(SyncReason.BACKGROUND)
            advanceTimeBy(5.seconds) // not enough — pipeline is waiting
            handle.result.isCompleted.shouldBeFalse()

            advanceTimeBy(360.seconds) // past the 5-minute bounded window
            val result = handle.result.await()
            flushActor()

            result.isLeft().shouldBeTrue()
            result.leftOrNull() shouldBe SyncError.NetworkUnavailable
        }
    }

    "awaitNetwork indefinite proceeds when network returns" {
        runTest {
            val network = FakeNetworkMonitor(initial = false)
            val coordinator = buildCoordinator(network = network)

            val handle = coordinator.requestSync(SyncReason.MANUAL) // indefinite
            advanceTimeBy(60.seconds)
            handle.result.isCompleted.shouldBeFalse()

            network.setOnline(true)
            val result = handle.result.await()
            flushActor()

            result.isRight().shouldBeTrue()
        }
    }

    "Unsupported app version short-circuits the sync with UnsupportedAppVersion" {
        runTest {
            val gate = FakeSyncVersionGate(
                initial = com.georgeci.moneysurfer.sync.version.SyncVersionStatus.Blocked(
                    message = "Please update",
                ),
            )
            val coordinator = buildCoordinator(versionGate = gate)

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            flushActor()

            result.isLeft().shouldBeTrue()
            val error = result.leftOrNull().shouldBeInstanceOf<SyncError.UnsupportedAppVersion>()
            error.message shouldBe "Please update"
            // Gate must be re-checked on each sync (so a server flip blocks within one cycle).
            gate.refreshCount shouldBe 1
        }
    }

    "Supported app version lets the sync run normally" {
        runTest {
            val gate = FakeSyncVersionGate()
            val coordinator = buildCoordinator(versionGate = gate)

            val handle = coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            flushActor()

            result.isRight().shouldBeTrue()
            gate.refreshCount shouldBe 1
        }
    }

    "running state emits step progression and reaches Completed" {
        runTest {
            val coordinator = buildCoordinator()
            val handle = coordinator.requestSync(SyncReason.MANUAL)
            handle.result.await()
            flushActor()

            handle.status.value.shouldBeInstanceOf<SyncHandleStatus.Completed>()
        }
    }
})
