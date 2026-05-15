package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * `requestSync` fails the test if invoked — the flag-off cases below must never reach
 * the coordinator. The flag-on path is exercised by `SyncCoordinatorImpl`'s own suite.
 */
private class RejectingCoordinator : SyncCoordinator {
    override val state: StateFlow<SyncState> = MutableStateFlow<SyncState>(SyncState.Idle).asStateFlow()
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow<LastSyncOutcome>(
        LastSyncOutcome.None,
    ).asStateFlow()
    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle =
        error("requestSync must not be called when SyncFeatureFlag is disabled")
    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() = Unit
}

class SyncCoordinatorWorkspaceSyncerSpec : StringSpec({

    "pushAll is a no-op when the sync feature flag is disabled" {
        runTest {
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = RejectingCoordinator(),
                session = InMemorySessionPointers(),
                syncFeatureFlag = SyncFeatureFlag(enabled = false),
            )

            syncer.pushAll()
        }
    }

    "syncAll is a no-op when the sync feature flag is disabled" {
        runTest {
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = RejectingCoordinator(),
                session = InMemorySessionPointers(),
                syncFeatureFlag = SyncFeatureFlag(enabled = false),
            )

            syncer.syncAll()
        }
    }

    "syncWorkspace still switches the active workspace when sync is disabled" {
        runTest {
            val session = InMemorySessionPointers()
            val syncer = SyncCoordinatorWorkspaceSyncer(
                syncCoordinator = RejectingCoordinator(),
                session = session,
                syncFeatureFlag = SyncFeatureFlag(enabled = false),
            )

            syncer.syncWorkspace(WorkspaceId("ws-42"))

            session.currentWorkspaceId.flow.first() shouldBe WorkspaceId("ws-42")
        }
    }
})
