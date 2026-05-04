package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface WorkspaceMemberDao {

    @Query("SELECT * FROM workspace_members")
    fun getAll(): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId")
    fun getByWorkspaceId(workspaceId: String): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE userId = :userId AND workspaceId = :workspaceId")
    suspend fun getById(userId: String, workspaceId: String): WorkspaceMemberEntity?

    @Insert
    suspend fun insert(entity: WorkspaceMemberEntity)

    @Insert
    suspend fun insertAll(entities: List<WorkspaceMemberEntity>)

    @Update
    suspend fun update(entity: WorkspaceMemberEntity)

    @Upsert
    suspend fun upsert(entity: WorkspaceMemberEntity)

    @Query("DELETE FROM workspace_members WHERE userId = :userId AND workspaceId = :workspaceId")
    suspend fun delete(userId: String, workspaceId: String)

    @Query("DELETE FROM workspace_members")
    suspend fun deleteAll()
}
