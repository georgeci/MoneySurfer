package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository

/**
 * Offline build has no Firestore — every remote-user operation is silently
 * dropped. Reads return null so callers fall back to local-only state.
 */
class NoOpUserRemoteRepository : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = null

    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = Unit

    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun findByEmail(email: String): UserId? = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}
