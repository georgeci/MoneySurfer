package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import kotlinx.coroutines.flow.Flow

/**
 * Workspace invitation repository.
 *
 * Status transitions (`markAccepted` / `markDeclined` / `cancel`) are read-modify-upsert
 * to preserve dual-write to Firestore via the outbox; no hard delete — invites stay as
 * audit history of who was invited and how it resolved.
 */
interface WorkspaceInviteRepository {
    fun getAll(): Flow<List<WorkspaceInvite>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceInvite>>
    fun getByEmail(email: String): Flow<List<WorkspaceInvite>>
    fun getByTargetUserId(userId: UserId): Flow<List<WorkspaceInvite>>
    suspend fun getById(id: WorkspaceInviteId): WorkspaceInvite?
    suspend fun upsert(invite: WorkspaceInvite)
    suspend fun cancel(id: WorkspaceInviteId)
    suspend fun markAccepted(id: WorkspaceInviteId)
    suspend fun markDeclined(id: WorkspaceInviteId)
}
