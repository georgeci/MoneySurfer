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
