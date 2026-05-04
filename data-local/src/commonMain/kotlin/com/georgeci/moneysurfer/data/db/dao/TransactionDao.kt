package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.CategorizedTransactionEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT
            transactions.id,
            transactions.workspaceId,
            transactions.accountId,
            transactions.amount,
            transactions.currencyCode,
            transactions.categoryId,
            categories.name AS categoryName,
            transactions.note,
            transactions.timestamp,
            transactions.type,
            transactions.status,
            transactions.updatedAt
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        ORDER BY transactions.timestamp DESC
        """,
    )
    fun getAllCategorized(): Flow<List<CategorizedTransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getByAccountId(accountId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT
            transactions.id,
            transactions.workspaceId,
            transactions.accountId,
            transactions.amount,
            transactions.currencyCode,
            transactions.categoryId,
            categories.name AS categoryName,
            transactions.note,
            transactions.timestamp,
            transactions.type,
            transactions.status,
            transactions.updatedAt
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        WHERE transactions.accountId = :accountId
        ORDER BY transactions.timestamp DESC
        """,
    )
    fun getByAccountIdCategorized(accountId: String): Flow<List<CategorizedTransactionEntity>>

    @Query("SELECT * FROM transactions WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getByWorkspaceId(workspaceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity)

    @Insert
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun nullifyCategoryId(categoryId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
