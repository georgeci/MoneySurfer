package com.georgeci.moneysurfer.data.sync

import arrow.core.right
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeSyncSettings
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * `requestSync` fails the test if invoked — the disabled cases below must never reach
 * the coordinator. The enabled path is exercised by `SyncCoordinatorImpl`'s own suite.
 */
private class RejectingCoordinator : SyncCoordinator {
    override val state: StateFlow<SyncState> = MutableStateFlow<SyncState>(SyncState.Idle).asStateFlow()
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow<LastSyncOutcome>(
        LastSyncOutcome.None,
    ).asStateFlow()
    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle =
        error("requestSync must not be called while sync is disabled")
    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() = Unit
}

/** Records every reason it is asked for and completes each request successfully. */
private class ReasonRecordingCoordinator : SyncCoordinator {
    val reasons: MutableList<SyncReason> = mutableListOf()
    override val state: StateFlow<SyncState> = MutableStateFlow<SyncState>(SyncState.Idle).asStateFlow()
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow<LastSyncOutcome>(
        LastSyncOutcome.None,
    ).asStateFlow()

    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle {
        reasons += reason
        return AlreadyCompletedHandle
    }

    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() = Unit
}

private object AlreadyCompletedHandle : SyncHandle {
    override val id: SyncRequestId = SyncRequestId("test-sync")
    override val status: StateFlow<SyncHandleStatus> =
        MutableStateFlow<SyncHandleStatus>(SyncHandleStatus.Completed(SyncSummary())).asStateFlow()
    override val steps: SharedFlow<SyncStep> = MutableSharedFlow()
    override val result: Deferred<SyncResult<SyncSummary>> =
        CompletableDeferred(SyncSummary().right())

    override fun cancel() = Unit
}

class SyncCoordinatorWorkspaceSyncerSpec : StringSpec({

    "pushAll is a no-op when sync is disabled" {
        runTest {
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = RejectingCoordinator(),
                sessionMutator = InMemorySessionPointers(),
                syncSettings = FakeSyncSettings(enabled = false),
            )

            syncer.pushAll()
        }
    }

    "syncAll is a no-op when sync is disabled" {
        runTest {
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = RejectingCoordinator(),
                sessionMutator = InMemorySessionPointers(),
                syncSettings = FakeSyncSettings(enabled = false),
            )

            syncer.syncAll()
        }
    }

    "a request that started while sync was enabled is not retried once it is disabled" {
        runTest {
            // The kill switch is a flow, so it can retract mid-session. Policy: start nothing new,
            // and let whatever is already running finish — so the gate is re-read per call rather
            // than latched once at construction.
            val coordinator = ReasonRecordingCoordinator()
            val settings = FakeSyncSettings(enabled = true)
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = coordinator,
                sessionMutator = InMemorySessionPointers(),
                syncSettings = settings,
            )

            syncer.pushAll()
            coordinator.reasons shouldBe listOf(SyncReason.LOCAL_CHANGE)

            settings.set(enabled = false)
            syncer.pushAll()
            syncer.syncAll()

            // No second request: the flag is consulted per call, not cached.
            coordinator.reasons shouldBe listOf(SyncReason.LOCAL_CHANGE)
        }
    }

    "syncWorkspace still switches the active workspace when sync is disabled" {
        runTest {
            val session = InMemorySessionPointers()
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = RejectingCoordinator(),
                sessionMutator = session,
                syncSettings = FakeSyncSettings(enabled = false),
            )

            syncer.syncWorkspace(WorkspaceId("ws-42"))

            session.currentWorkspaceId.first() shouldBe WorkspaceId("ws-42")
        }
    }
})
