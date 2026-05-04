package com.georgeci.moneysurfer.data.sync

/**
 * Provides the authoritative list of workspace IDs for the current user from the remote
 * source (`users/{uid}.workspaceIds`). Used by [PullRemoteChangesUseCaseImpl] to discover
 * workspaces not yet present in the local database.
 */
interface UserWorkspacesProvider {
    suspend fun workspaceIds(): List<String>
    /** Workspace IDs the user has been invited to but not yet joined. */
    suspend fun invitedWorkspaceIds(): List<String>
}
