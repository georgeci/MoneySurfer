package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import org.koin.core.annotation.Single

/**
 * [WorkspaceSyncer] implementation that delegates to [SyncCoordinator].
 *
 * [pushAll] uses [SyncReason.LOCAL_CHANGE] → [SyncScope.UploadOnly]: drains the outbox
 * without a remote workspace-list fetch. Used post-create-workspace where the new workspace
 * and its member row need to land on Firestore before `addWorkspaceRef` is called.
 *
 * [syncAll] uses [SyncReason.MANUAL] → [SyncScope.AllUserData]: fetches
 * `users/{uid}.workspaceIds` via [UserWorkspacesProvider] and runs a cursor-based pull for
 * every workspace, including ones not yet in the local database.
 *
 * [syncWorkspace] sets [SessionPointers.currentWorkspaceId] to the target workspace and
 * uses [SyncReason.SWIPE_REFRESH] → [SyncScope.ActiveWorkspace]: pulls only that workspace.
 * Used after accepting an invite so only the newly joined workspace is hydrated.
 */
@Single(binds = [WorkspaceSyncer::class])
class SyncCoordinatorWorkspaceSyncer(
    private val syncCoordinator: SyncCoordinator,
    private val session: SessionPointers,
    private val syncFeatureFlag: SyncFeatureFlag,
) : WorkspaceSyncer {

    // When the sync feature is off we want every use-case-driven trigger
    // (PostAuthBootstrap, CreateWorkspace, AcceptInvite, RefreshIncomingInvites)
    // to no-op instead of hitting Firestore. Gating at the syncer keeps the
    // use cases agnostic of the flag.

    override suspend fun pushAll() {
        if (!syncFeatureFlag.enabled) return
        syncCoordinator.requestSync(SyncReason.LOCAL_CHANGE)
            .result.await()
            .fold(ifLeft = { throw it.toException() }, ifRight = { })
    }

    override suspend fun syncAll() {
        if (!syncFeatureFlag.enabled) return
        syncCoordinator.requestSync(SyncReason.MANUAL)
            .result.await()
            .fold(ifLeft = { throw it.toException() }, ifRight = { })
    }

    override suspend fun syncWorkspace(workspaceId: WorkspaceId) {
        // Switch the active workspace pointer even when sync is off — callers
        // (e.g. AcceptInviteUseCase) rely on this to move the session to the
        // newly joined workspace. Only skip the network round-trip.
        session.currentWorkspaceId.set(workspaceId)
        if (!syncFeatureFlag.enabled) return
        syncCoordinator.requestSync(SyncReason.SWIPE_REFRESH)
            .result.await()
            .fold(ifLeft = { throw it.toException() }, ifRight = { })
    }

    private fun SyncError.toException(): RuntimeException = when (this) {
        SyncError.Cancelled -> RuntimeException("Sync cancelled")
        SyncError.NetworkUnavailable -> RuntimeException("Network unavailable")
        SyncError.AuthRequired -> RuntimeException("Auth required")
        SyncError.PermissionDenied -> RuntimeException("PERMISSION_DENIED: workspace access denied")
        is SyncError.UnsupportedAppVersion -> RuntimeException(message)
        is SyncError.StorageError -> RuntimeException("Storage error", cause)
        is SyncError.Unknown -> RuntimeException(cause.message ?: "Sync failed", cause)
    }
}
