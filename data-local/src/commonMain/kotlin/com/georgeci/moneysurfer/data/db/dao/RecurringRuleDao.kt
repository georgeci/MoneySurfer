package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules")
    fun getAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE workspaceId = :workspaceId")
    fun getByWorkspaceId(workspaceId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: String): RecurringRuleEntity?

    @Insert
    suspend fun insert(entity: RecurringRuleEntity)

    @Update
    suspend fun update(entity: RecurringRuleEntity)

    @Upsert
    suspend fun upsertAll(entities: List<RecurringRuleEntity>)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
