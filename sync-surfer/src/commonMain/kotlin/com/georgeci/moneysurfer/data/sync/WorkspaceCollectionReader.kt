package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.plugin.RemoteDocument

interface WorkspaceCollectionReader {
    /**
     * Fetches the workspace root document (`/workspaces/{workspaceId}`).
     * Returns null if the document does not exist or the caller lacks access.
     * Used by [PullRemoteChangesUseCaseImpl] to hydrate the local [WorkspaceEntity]
     * before subcollection pulls (accounts / categories / transactions all have a
     * Room FK on `workspaceId`).
     */
    suspend fun fetchWorkspaceDoc(workspaceId: String): RemoteDocument?

    suspend fun fetchUpdatedSince(
        workspaceId: String,
        collectionName: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument>

    /**
     * Fetches invite documents from a workspace the caller has been invited to but
     * has not yet joined. Adds a `targetUserId == uid` constraint so the query
     * satisfies Firestore rules for non-members (see firestore.rules §invites).
     */
    suspend fun fetchInvitesForUser(
        workspaceId: String,
        uid: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument>
}
