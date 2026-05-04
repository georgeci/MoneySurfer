package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

/**
 * Membership roster repository.
 *
 * Soft-delete only — `markRemoved` and `markLeft` flip `status` and stamp the matching
 * timestamp, never erase the row. Firestore rules forbid hard deletes; mirrored locally
 * to keep history readable for the rest of the workspace.
 */
interface WorkspaceMemberRepository {
    fun getAll(): Flow<List<WorkspaceMember>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>>
    fun getByUserId(userId: UserId): Flow<List<WorkspaceMember>>
    suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember?
    suspend fun insert(member: WorkspaceMember)
    suspend fun update(member: WorkspaceMember)

    /** Owner removes a member: status → REMOVED, removedAt = now, addedByUserId untouched. */
    suspend fun markRemoved(userId: UserId, workspaceId: WorkspaceId, removedByUserId: UserId?)

    /** Member voluntarily leaves: status → LEFT, leftAt = now. */
    suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId)
}
