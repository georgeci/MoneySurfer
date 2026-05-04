package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

/**
 * Triggers sync operations without exposing the sync infrastructure to domain use cases.
 *
 * [pushAll] drains the outbox (push-only, no remote discovery).
 * [syncAll] fetches the authoritative workspace list from the remote user doc and
 * runs a full cursor-based pull for every workspace — including ones not yet local.
 * [syncWorkspace] pulls a single specific workspace; used after accepting an invite so
 * only the newly joined workspace is hydrated without re-syncing everything.
 */
interface WorkspaceSyncer {
    /** Push all pending outbox mutations. Throws on error. */
    suspend fun pushAll()

    /** Push outbox + pull all user workspaces (including undiscovered ones). Throws on error. */
    suspend fun syncAll()

    /**
     * Push outbox + pull a single workspace by [workspaceId].
     * Sets the active workspace to [workspaceId] for the duration of the sync so
     * [SyncScope.ActiveWorkspace] targets the correct workspace.
     */
    suspend fun syncWorkspace(workspaceId: WorkspaceId)
}
