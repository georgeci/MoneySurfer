package com.georgeci.moneysurfer.sync.fixtures.coordinator

import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.fixtures.network.FakeNetworkMonitor
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammablePullUseCase
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammableRecalculateUseCase
import com.georgeci.moneysurfer.sync.fixtures.usecase.ProgrammableUploadUseCase
import com.georgeci.moneysurfer.sync.fixtures.version.FakeSyncVersionGate
import com.georgeci.moneysurfer.sync.internal.coordinator.SyncCoordinatorImpl
import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import com.georgeci.moneysurfer.sync.telemetry.SyncTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Wraps a [SyncCoordinatorImpl] with all its collaborators replaced by
 * in-memory fakes. Tests instantiate [SyncCoordinatorTestHarness], drive the
 * coordinator via [coordinator], and call [close] to tear down the actor.
 *
 * Defaults to [Dispatchers.Unconfined] so coroutines run inline — works without
 * `runTest { }` for tests that don't need virtual time.
 *
 * For tests that need `advanceTimeBy` / `delay` simulation, pass a
 * `TestCoroutineScheduler`-backed dispatcher via [scope] instead.
 */
class SyncCoordinatorTestHarness(
    val network: FakeNetworkMonitor = FakeNetworkMonitor(initial = true),
    val upload: ProgrammableUploadUseCase = ProgrammableUploadUseCase(),
    val pull: ProgrammablePullUseCase = ProgrammablePullUseCase(),
    val recalculate: ProgrammableRecalculateUseCase = ProgrammableRecalculateUseCase(),
    val versionGate: FakeSyncVersionGate = FakeSyncVersionGate(),
    val telemetry: SyncTelemetry = SyncTelemetry.NoOp,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    idSequence: List<String> = (1..10_000).map { "req-$it" },
) {
    private val ids = idSequence.iterator()

    val coordinator: SyncCoordinatorImpl = SyncCoordinatorImpl(
        appScope = ApplicationScope(delegate = scope),
        networkMonitor = network,
        versionGate = versionGate,
        uploadPendingChangesUseCase = upload,
        pullRemoteChangesUseCase = pull,
        recalculateLocalProjectionsUseCase = recalculate,
        telemetry = telemetry,
        newRequestId = { SyncRequestId(ids.next()) },
    )

    fun close() { scope.cancel() }
}

/**
 * Convenience: build the harness, run [block], always close.
 */
inline fun <R> withSyncCoordinatorHarness(
    network: FakeNetworkMonitor = FakeNetworkMonitor(initial = true),
    upload: ProgrammableUploadUseCase = ProgrammableUploadUseCase(),
    pull: ProgrammablePullUseCase = ProgrammablePullUseCase(),
    recalculate: ProgrammableRecalculateUseCase = ProgrammableRecalculateUseCase(),
    versionGate: FakeSyncVersionGate = FakeSyncVersionGate(),
    block: (SyncCoordinatorTestHarness) -> R,
): R {
    val harness = SyncCoordinatorTestHarness(
        network = network,
        upload = upload,
        pull = pull,
        recalculate = recalculate,
        versionGate = versionGate,
    )
    return try { block(harness) } finally { harness.close() }
}
