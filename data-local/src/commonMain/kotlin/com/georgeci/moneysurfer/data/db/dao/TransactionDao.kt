package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.CategorizedTransactionEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryMonthlyTotalEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY operationDate DESC, operationAt DESC, createdAt DESC")
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
            transactions.merchant,
            transactions.tags,
            transactions.operationAt,
            transactions.operationDate,
            transactions.createdAt,
            transactions.type,
            transactions.status,
            transactions.updatedAt,
            transactions.transferId,
            transactions.recurringRuleId
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        ORDER BY transactions.operationDate DESC, transactions.operationAt DESC, transactions.createdAt DESC
        """,
    )
    fun getAllCategorized(): Flow<List<CategorizedTransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId ORDER BY operationDate DESC, operationAt DESC, createdAt DESC",
    )
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
            transactions.merchant,
            transactions.tags,
            transactions.operationAt,
            transactions.operationDate,
            transactions.createdAt,
            transactions.type,
            transactions.status,
            transactions.updatedAt,
            transactions.transferId,
            transactions.recurringRuleId
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        WHERE transactions.accountId = :accountId
        ORDER BY transactions.operationDate DESC, transactions.operationAt DESC, transactions.createdAt DESC
        """,
    )
    fun getByAccountIdCategorized(accountId: String): Flow<List<CategorizedTransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE workspaceId = :workspaceId ORDER BY operationDate DESC, operationAt DESC, createdAt DESC",
    )
    fun getByWorkspaceId(workspaceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    /**
     * Full-text search over notes and merchants within a workspace, newest first.
     *
     * A bare `MATCH` against the virtual table spans both indexed columns, so this needs no
     * per-column syntax — see [com.georgeci.moneysurfer.data.db.entity.TransactionFtsEntity].
     *
     * [query] is raw FTS4 MATCH syntax, never user input — build it with
     * [com.georgeci.moneysurfer.data.db.FtsQuery.fromUserInput], which escapes
     * operators and adds prefix wildcards.
     */
    @Query(
        """
        SELECT transactions.* FROM transactions
        JOIN transactions_fts ON transactions_fts.rowid = transactions.rowid
        WHERE transactions.workspaceId = :workspaceId AND transactions_fts MATCH :query
        ORDER BY transactions.operationDate DESC, transactions.operationAt DESC, transactions.createdAt DESC
        """,
    )
    fun searchByText(workspaceId: String, query: String): Flow<List<TransactionEntity>>

    /**
     * Per-month totals for [categoryIds] within a workspace, for one transaction type.
     *
     * Grouped in SQLite rather than in memory: the category detail screen wants six months of
     * history for a whole subtree, and streaming every matching row up to the domain just to sum
     * it would scale with transaction volume instead of with the twenty-odd cells it draws.
     *
     * The window is compared against `operationDate`, an ISO `YYYY-MM-DD` string, so a plain
     * string range is a correct date range. Rows predating the column carry `''`, which sorts
     * below every real date and is excluded by [fromDate] without a special case.
     *
     * [toDateExclusive] is the first day of the month *after* the last month wanted — half-open
     * so the caller never has to know how long the final month is.
     */
    @Query(
        """
        SELECT
            transactions.categoryId AS categoryId,
            substr(transactions.operationDate, 1, 7) AS month,
            SUM(ABS(transactions.amount)) AS totalMinor,
            COUNT(*) AS transactionCount
        FROM transactions
        WHERE transactions.workspaceId = :workspaceId
            AND transactions.categoryId IN (:categoryIds)
            AND transactions.type = :type
            AND transactions.status = 'ACTUAL'
            AND transactions.operationDate >= :fromDate
            AND transactions.operationDate < :toDateExclusive
        GROUP BY transactions.categoryId, month
        """,
    )
    fun getMonthlyTotalsByCategory(
        workspaceId: String,
        categoryIds: List<String>,
        type: String,
        fromDate: String,
        toDateExclusive: String,
    ): Flow<List<CategoryMonthlyTotalEntity>>

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
