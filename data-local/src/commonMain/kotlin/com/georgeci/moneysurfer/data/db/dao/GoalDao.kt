package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals")
    fun getAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun getByWorkspaceId(workspaceId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: String): GoalEntity?

    @Insert
    suspend fun insert(entity: GoalEntity)

    @Update
    suspend fun update(entity: GoalEntity)

    @Upsert
    suspend fun upsertAll(entities: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}
