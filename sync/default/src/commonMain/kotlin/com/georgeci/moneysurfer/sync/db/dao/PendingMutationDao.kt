package com.georgeci.moneysurfer.sync.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMutationDao {

    @Insert
    suspend fun insert(entity: PendingMutationEntity)

    @Query(
        """
        SELECT * FROM pending_mutations
        WHERE status = 'PENDING'
          AND (:workspaceId IS NULL OR workspaceId = :workspaceId OR workspaceId IS NULL)
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(workspaceId: String?, limit: Int): List<PendingMutationEntity>

    @Query("UPDATE pending_mutations SET status = 'IN_FLIGHT' WHERE id IN (:ids)")
    suspend fun markInFlight(ids: List<String>)

    @Query("DELETE FROM pending_mutations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query(
        """
        UPDATE pending_mutations
        SET status = 'PENDING',
            attempts = attempts + 1,
            lastError = :error
        WHERE id = :id
        """,
    )
    suspend fun markFailed(id: String, error: String)

    @Query("SELECT COUNT(*) FROM pending_mutations WHERE status != 'IN_FLIGHT'")
    fun pendingCount(): Flow<Int>

    @Query("DELETE FROM pending_mutations")
    suspend fun deleteAll()
}
