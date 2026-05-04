package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspaces")
    fun getAll(): Flow<List<WorkspaceEntity>>

    @Query(
        """
        SELECT w.*
        FROM workspaces w
        INNER JOIN workspace_members wm ON wm.workspaceId = w.id
        WHERE wm.userId = :userId
        """,
    )
    fun getByUserId(userId: String): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getById(id: String): WorkspaceEntity?

    @Insert
    suspend fun insert(entity: WorkspaceEntity)

    @Insert
    suspend fun insertAll(entities: List<WorkspaceEntity>)

    @Update
    suspend fun update(entity: WorkspaceEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WorkspaceEntity>)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM workspaces")
    suspend fun deleteAll()
}
