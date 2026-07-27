package com.georgeci.moneysurfer.sync.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMutationDao {

    /**
     * Insert-if-absent: queues the mutation unless an identical one is already waiting.
     *
     * Renaming an account five times used to queue five identical pushes, because outbox rows carry
     * no payload — each one re-reads the entity at push time, so they all push the same current
     * value. That is redundant, not incorrect, but it is redundant for *every* entity type, and a
     * settings screen bound to a toggle produces such runs routinely.
     *
     * **Scoping to `PENDING` is the correctness part.** A write landing while a row is `IN_FLIGHT`
     * must produce a *new* row: the in-flight push already read the entity, so without a second row
     * the change made after that read would never be sent. A plain unique index cannot express
     * "unique among pending rows only" — Room's `@Index` has no `WHERE` clause — so the condition
     * lives in the statement.
     *
     * **`workspaceId` is part of the identity, not decoration.** Most entity ids are UUIDs, so
     * `entityType` + `entityId` would be enough for them — but `WORKSPACE_MEMBER` rows carry
     * `entityId = userId` and `scopeKey = workspaceId`, and one user is a member of many
     * workspaces. Matching without the scope would let leaving W1 swallow the enqueue for leaving
     * W2, and the surviving row pushes only its own workspace: W2 would keep the user ACTIVE on
     * Firestore forever. `IS` rather than `=` because the column is nullable — settings rows carry
     * no workspace, and SQL `=` never matches NULL to NULL.
     */
    @Query(
        """
        INSERT INTO pending_mutations (id, entityType, entityId, operation, workspaceId, createdAt, attempts, status, lastError)
        SELECT :id, :entityType, :entityId, :operation, :workspaceId, :createdAt, :attempts, :status, :lastError
        WHERE NOT EXISTS (
            SELECT 1 FROM pending_mutations
            WHERE entityType = :entityType
              AND entityId = :entityId
              AND operation = :operation
              AND workspaceId IS :workspaceId
              AND status = 'PENDING'
        )
        """,
    )
    @Suppress("LongParameterList") // Mirrors the entity: an @Insert cannot express the guard.
    suspend fun insertIfAbsent(
        id: String,
        entityType: String,
        entityId: String,
        operation: String,
        workspaceId: String?,
        createdAt: Long,
        attempts: Int,
        status: String,
        lastError: String?,
    )

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
