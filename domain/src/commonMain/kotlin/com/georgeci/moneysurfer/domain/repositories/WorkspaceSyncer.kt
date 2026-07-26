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
    /**
     * Push all pending outbox mutations. Throws on error.
     *
     * Returns `false` when sync is switched off and nothing was pushed, so a caller cannot mistake
     * "disabled" for "landed" — the asymmetry that filled `users/{uid}.workspaceIds` with refs to
     * documents that were never created (issue #342). Callers that write remote state on the
     * strength of a successful push must branch on this instead of re-reading the setting
     * themselves: two reads of a live setting can disagree.
     */
    suspend fun pushAll(): Boolean

    /** Push outbox + pull all user workspaces (including undiscovered ones). Throws on error. */
    suspend fun syncAll()

    /**
     * Push outbox + pull a single workspace by [workspaceId].
     * Sets the active workspace to [workspaceId] for the duration of the sync so
     * [SyncScope.ActiveWorkspace] targets the correct workspace.
     */
    suspend fun syncWorkspace(workspaceId: WorkspaceId)
}
