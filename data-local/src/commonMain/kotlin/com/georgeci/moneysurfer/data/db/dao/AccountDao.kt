package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC, id ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE workspaceId = :workspaceId ORDER BY sortOrder ASC, name ASC, id ASC")
    fun getByWorkspaceId(workspaceId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert
    suspend fun insert(entity: AccountEntity)

    @Insert
    suspend fun insertAll(entities: List<AccountEntity>)

    @Update
    suspend fun update(entity: AccountEntity)

    @Upsert
    suspend fun upsertAll(entities: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("UPDATE accounts SET balance = balance + :delta, updatedAt = :updatedAt WHERE id = :id")
    suspend fun applyDelta(id: String, delta: Long, updatedAt: Long)

    @Query("UPDATE accounts SET balance = :balance, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setBalance(id: String, balance: Long, updatedAt: Long)

    @Query(
        "UPDATE accounts SET archived = :archived, archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun setArchived(id: String, archived: Boolean, archivedAt: Long?, updatedAt: Long)

    /** Position one past the last account of [workspaceId], i.e. where a new account belongs. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts WHERE workspaceId = :workspaceId")
    suspend fun nextSortOrder(workspaceId: String): Int

    @Query("UPDATE accounts SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    /**
     * Applies a whole reorder as one write. Row-at-a-time outside a transaction would leave the
     * list half-reordered for anyone observing the table between the updates, and a failure
     * midway would leave it there for good.
     */
    @Transaction
    suspend fun setSortOrders(sortOrders: Map<String, Int>, updatedAt: Long) {
        sortOrders.forEach { (id, sortOrder) -> setSortOrder(id, sortOrder, updatedAt) }
    }
}
