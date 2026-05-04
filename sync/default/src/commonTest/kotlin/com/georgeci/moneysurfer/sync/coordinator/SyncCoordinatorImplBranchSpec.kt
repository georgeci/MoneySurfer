package com.georgeci.moneysurfer.sync.coordinator

import arrow.core.left
import arrow.core.right
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.fixtures.network.FakeNetworkMonitor
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammablePullUseCase
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammableRecalculateUseCase
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammableUploadUseCase
import com.georgeci.moneysurfer.sync.fixtures.version.FakeSyncVersionGate
import com.georgeci.moneysurfer.sync.internal.coordinator.SyncCoordinatorImpl
import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.UploadProgress
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Branch-coverage tests for [SyncCoordinatorImpl] that don't need virtual time.
 * Each test wires its own [CoroutineScope] backed by [Dispatchers.Unconfined]
 * so coroutines run inline — no `runTest { }` wrapper.
 *
 * Long-running stages are paused with [CompletableDeferred] gates rather than
 * `delay`, which keeps everything deterministic in real time.
 */
private class CoordinatorHarness(
    network: FakeNetworkMonitor = FakeNetworkMonitor(initial = true),
    upload: ProgrammableUploadUseCase = ProgrammableUploadUseCase(),
    pull: ProgrammablePullUseCase = ProgrammablePullUseCase(),
    recalculate: ProgrammableRecalculateUseCase = ProgrammableRecalculateUseCase(),
    versionGate: FakeSyncVersionGate = FakeSyncVersionGate(),
    idSequence: List<String> = (1..1000).map { "req-$it" },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val ids = idSequence.iterator()

    val coordinator: SyncCoordinatorImpl = SyncCoordinatorImpl(
        appScope = ApplicationScope(delegate = scope),
        networkMonitor = network,
        versionGate = versionGate,
        uploadPendingChangesUseCase = upload,
        pullRemoteChangesUseCase = pull,
        recalculateLocalProjectionsUseCase = recalculate,
        newRequestId = { SyncRequestId(ids.next()) },
    )

    fun close() { scope.cancel() }
}

class SyncCoordinatorImplBranchSpec : StringSpec({

    "cancelAllQueued with empty queue is a no-op" {
        // Covers cancelPendingInternal early return when pending == null.
        val h = CoordinatorHarness()
        try {
            h.coordinator.cancelAllQueued()
            h.coordinator.state.value shouldBe SyncState.Idle
            h.coordinator.lastOutcome.value shouldBe LastSyncOutcome.None
        } finally { h.close() }
    }

    "cancelAllQueued cancels queued requests but keeps the running one" {
        val gate = CompletableDeferred<Unit>()
        val upload = ProgrammableUploadUseCase { _, _, _ ->
            gate.await()
            UploadSummary(uploadedCount = 0).right()
        }
        val h = CoordinatorHarness(upload = upload)
        try {
            val running = h.coordinator.requestSync(SyncReason.MANUAL)
            val queued = h.coordinator.requestSync(SyncReason.MANUAL)

            h.coordinator.cancelAllQueued()

            queued.result.isCompleted.shouldBeTrue()
            queued.result.await().leftOrNull() shouldBe SyncError.Cancelled
            queued.status.value shouldBe SyncHandleStatus.Cancelled

            running.result.isCompleted.shouldBeFalse()

            // Let running finish so the harness shuts down cleanly.
            gate.complete(Unit)
            running.result.await().isRight().shouldBeTrue()
        } finally { h.close() }
    }

    "ReplaceQueued cancels existing pending and replaces it" {
        val gate = CompletableDeferred<Unit>()
        val upload = ProgrammableUploadUseCase { _, _, _ ->
            gate.await()
            UploadSummary(uploadedCount = 0).right()
        }
        val h = CoordinatorHarness(upload = upload)
        try {
            val running = h.coordinator.requestSync(SyncReason.MANUAL)
            val queued = h.coordinator.requestSync(SyncReason.MANUAL)
            val replacement = h.coordinator.requestSync(SyncReason.MANUAL, SyncMode.ReplaceQueued)

            queued.result.isCompleted.shouldBeTrue()
            queued.result.await().leftOrNull() shouldBe SyncError.Cancelled

            replacement.result.isCompleted.shouldBeFalse()
            running.result.isCompleted.shouldBeFalse()

            gate.complete(Unit) // unblock the running upload — running and replacement both succeed
            running.result.await().isRight().shouldBeTrue()
            replacement.result.await().isRight().shouldBeTrue()
        } finally { h.close() }
    }

    "Enqueue merges into existing pending request" {
        val gate = CompletableDeferred<Unit>()
        val upload = ProgrammableUploadUseCase { _, _, _ ->
            gate.await()
            UploadSummary(uploadedCount = 0).right()
        }
        val h = CoordinatorHarness(upload = upload)
        try {
            h.coordinator.requestSync(SyncReason.MANUAL) // running, gated
            val first = h.coordinator.requestSync(SyncReason.MANUAL) // pending
            val second = h.coordinator.requestSync(SyncReason.LOCAL_CHANGE) // merged into pending

            // Both queued handles share the same merged request when it eventually runs.
            gate.complete(Unit)
            first.result.await().isRight().shouldBeTrue()
            second.result.await().isRight().shouldBeTrue()
        } finally { h.close() }
    }

    "cancelCurrent with no running sync is a no-op" {
        // Covers handleCancelCurrent early return.
        val h = CoordinatorHarness()
        try {
            h.coordinator.cancelCurrent()
            h.coordinator.state.value shouldBe SyncState.Idle
            h.coordinator.lastOutcome.value shouldBe LastSyncOutcome.None
        } finally { h.close() }
    }

    "handle cancel of unknown id is a no-op" {
        val h = CoordinatorHarness()
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            handle.result.await().isRight().shouldBeTrue()
            // Now nothing is running and pending is empty — re-cancelling has nowhere to go.
            handle.cancel()
            h.coordinator.state.value shouldBe SyncState.Idle
        } finally { h.close() }
    }

    "thrown Throwable from upload becomes SyncError.Unknown" {
        val cause = IllegalStateException("boom")
        val upload = ProgrammableUploadUseCase { _, _, _ -> throw cause }
        val h = CoordinatorHarness(upload = upload)
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            result.isLeft().shouldBeTrue()
            val error = result.leftOrNull().shouldBeInstanceOf<SyncError.Unknown>()
            error.cause shouldBe cause
            h.coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Failed>()
        } finally { h.close() }
    }

    "Either.Left(Cancelled) returned from upload completes as cancelled" {
        // Drives the `error is SyncError.Cancelled` branch in startWorkerIfNeeded.
        val upload = ProgrammableUploadUseCase { _, _, _ -> SyncError.Cancelled.left() }
        val h = CoordinatorHarness(upload = upload)
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            val result = handle.result.await()
            result.leftOrNull() shouldBe SyncError.Cancelled
            h.coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Cancelled>()
        } finally { h.close() }
    }

    "upload progress emits UploadingEntity into running state" {
        val gate = CompletableDeferred<Unit>()
        val upload = ProgrammableUploadUseCase { _, onProgress, _ ->
            onProgress(UploadProgress("TRANSACTION", current = 3, total = 5))
            gate.await()
            UploadSummary(uploadedCount = 5).right()
        }
        val h = CoordinatorHarness(upload = upload)
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            // Worker is suspended on the gate after the progress emit — state holds the step.
            val running = h.coordinator.state.value.shouldBeInstanceOf<SyncState.Running>()
            val step = running.currentStep.shouldBeInstanceOf<SyncStep.UploadingEntity>()
            step.entityType shouldBe "TRANSACTION"
            step.current shouldBe 3
            step.total shouldBe 5

            gate.complete(Unit)
            handle.result.await().isRight().shouldBeTrue()
        } finally { h.close() }
    }

    "pull progress emits PullingCollection into running state" {
        val gate = CompletableDeferred<Unit>()
        val pull = ProgrammablePullUseCase { _, onProgress, _ ->
            onProgress(PullProgress("transactions", current = 4, total = 9))
            gate.await()
            PullSummary(downloadedCount = 4, conflictCount = 0).right()
        }
        val h = CoordinatorHarness(pull = pull)
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            val running = h.coordinator.state.value.shouldBeInstanceOf<SyncState.Running>()
            val step = running.currentStep.shouldBeInstanceOf<SyncStep.PullingCollection>()
            step.collection shouldBe "transactions"
            step.current shouldBe 4
            step.total shouldBe 9

            gate.complete(Unit)
            handle.result.await().isRight().shouldBeTrue()
        } finally { h.close() }
    }

    "handle cancel on the only running request cancels the worker job" {
        // Covers handleCancelHandle's `all children cancelled` branch on the running request.
        val gate = CompletableDeferred<Unit>()
        val upload = ProgrammableUploadUseCase { _, _, _ ->
            gate.await()
            UploadSummary(uploadedCount = 0).right()
        }
        val h = CoordinatorHarness(upload = upload)
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            handle.cancel()

            val result = handle.result.await()
            result.leftOrNull() shouldBe SyncError.Cancelled
            handle.status.value shouldBe SyncHandleStatus.Cancelled
            h.coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Cancelled>()
        } finally { h.close() }
    }

    "mid-pipeline cancel surfaces SyncCancelledException to the worker catch" {
        // Drives the SyncCancelledException catch in startWorkerIfNeeded:
        // upload completes normally, but cancelToken was flipped during upload —
        // the next `throwIfCancelled()` after upload throws SyncCancelledException.
        val handleSlot = CompletableDeferred<SyncHandle>()
        val upload = ProgrammableUploadUseCase { _, _, _ ->
            val ownHandle = handleSlot.await()
            ownHandle.cancel() // flips this request's own cancel token mid-pipeline
            UploadSummary(uploadedCount = 0).right()
        }
        val h = CoordinatorHarness(upload = upload)
        try {
            val handle = h.coordinator.requestSync(SyncReason.MANUAL)
            handleSlot.complete(handle)
            val result = handle.result.await()
            result.leftOrNull() shouldBe SyncError.Cancelled
            h.coordinator.lastOutcome.value.shouldBeInstanceOf<LastSyncOutcome.Cancelled>()
        } finally { h.close() }
    }

    "default newRequestId factory generates uuid-shaped ids" {
        // Constructs the coordinator without overriding `newRequestId` to exercise the default.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = SyncCoordinatorImpl(
                appScope = ApplicationScope(delegate = scope),
                networkMonitor = FakeNetworkMonitor(initial = true),
                versionGate = FakeSyncVersionGate(),
                uploadPendingChangesUseCase = ProgrammableUploadUseCase(),
                pullRemoteChangesUseCase = ProgrammablePullUseCase(),
                recalculateLocalProjectionsUseCase = ProgrammableRecalculateUseCase(),
            )
            val handle = coordinator.requestSync(SyncReason.MANUAL)
            handle.id.value.length shouldBe 36 // UUID canonical form
            handle.result.await().isRight().shouldBeTrue()
        } finally { scope.cancel() }
    }
})
