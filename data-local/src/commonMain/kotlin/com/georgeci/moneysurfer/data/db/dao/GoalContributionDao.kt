package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.GoalContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {

    @Query("SELECT * FROM goal_contributions WHERE workspaceId = :workspaceId ORDER BY occurredOn DESC")
    fun getByWorkspaceId(workspaceId: String): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY occurredOn DESC, createdAt DESC")
    fun getByGoalId(goalId: String): Flow<List<GoalContributionEntity>>

    /** Rows of a goal as a one-shot read — the outbox needs them before a cascade drops them. */
    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId")
    suspend fun listByGoalId(goalId: String): List<GoalContributionEntity>

    /** A goal's saved amount. Signed sum, so withdrawals subtract; 0 when there are no rows. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM goal_contributions WHERE goalId = :goalId")
    fun observeTotalByGoalId(goalId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM goal_contributions WHERE goalId = :goalId")
    suspend fun totalByGoalId(goalId: String): Long

    @Query("SELECT * FROM goal_contributions WHERE id = :id")
    suspend fun getById(id: String): GoalContributionEntity?

    @Insert
    suspend fun insert(entity: GoalContributionEntity)

    @Update
    suspend fun update(entity: GoalContributionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<GoalContributionEntity>)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM goal_contributions")
    suspend fun deleteAll()
}
