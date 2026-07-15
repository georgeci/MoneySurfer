package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface WorkspaceInviteDao {

    @Query("SELECT * FROM workspace_invites")
    fun getAll(): Flow<List<WorkspaceInviteEntity>>

    @Query("SELECT * FROM workspace_invites WHERE workspaceId = :workspaceId")
    fun getByWorkspaceId(workspaceId: String): Flow<List<WorkspaceInviteEntity>>

    @Query("SELECT * FROM workspace_invites WHERE email = :email")
    fun getByEmail(email: String): Flow<List<WorkspaceInviteEntity>>

    @Query("SELECT * FROM workspace_invites WHERE targetUserId = :userId")
    fun getByTargetUserId(userId: String): Flow<List<WorkspaceInviteEntity>>

    @Query("SELECT * FROM workspace_invites WHERE id = :id")
    suspend fun getById(id: String): WorkspaceInviteEntity?

    // Id of a still-joinable invite in [workspaceId] that admits the given member —
    // matched by targetUserId or, for email-only invites, a case-insensitive email
    // match. Stamped onto the pushed member doc so Firestore rules can verify the
    // accept-invite self-create (issue #152). PENDING is preferred over ACCEPTED so a
    // fresh invite wins over a stale terminal one; nulls when nothing admits the member.
    @Query(
        """
        SELECT id FROM workspace_invites
        WHERE workspaceId = :workspaceId
          AND status IN ('PENDING', 'ACCEPTED')
          AND (targetUserId = :userId OR (:email IS NOT NULL AND lower(email) = lower(:email)))
        ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findJoinableInviteId(
        workspaceId: String,
        userId: String,
        email: String?,
    ): String?

    @Insert
    suspend fun insert(entity: WorkspaceInviteEntity)

    @Insert
    suspend fun insertAll(entities: List<WorkspaceInviteEntity>)

    @Update
    suspend fun update(entity: WorkspaceInviteEntity)

    @Upsert
    suspend fun upsert(entity: WorkspaceInviteEntity)

    @Query("DELETE FROM workspace_invites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM workspace_invites")
    suspend fun deleteAll()
}
