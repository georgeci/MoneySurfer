package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.CategorizedTransactionEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryMonthlyTotalEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionTotalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Ordering is always `(operationDate, operationAt, createdAt) DESC`: the business date groups the
 * day, the instant orders within it, and `createdAt` breaks ties between rows sharing an instant —
 * which backdated batches and CSV imports routinely do.
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY operationDate DESC, operationAt DESC, createdAt DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId ORDER BY operationDate DESC, operationAt DESC, createdAt DESC",
    )
    fun getByAccountId(accountId: String): Flow<List<TransactionEntity>>

    /**
     * Newest-first page of categorized rows inside an optional `[from, to]` date window, for one
     * account or — when [accountId] is null — every account.
     *
     * One query covers all four combinations via nullable bounds, which keeps a single execution
     * plan in Room's cache instead of four near-identical statements. `operationDate` is ISO
     * `YYYY-MM-DD` text, so `>=` / `<=` compare correctly, and the composite
     * `(accountId, operationDate DESC, ...)` index on [TransactionEntity] serves both the filter
     * and the ORDER BY for the account-scoped case.
     *
     * Opening balances are excluded here rather than in the caller — they are an account
     * artefact, never a list row. Both the current and the legacy spelling are named because
     * `TransactionRepositoryImpl.parseType` still maps `INITIAL_BALANCE` onto the same type.
     *
     * [limit] is mandatory: paging grows the limit rather than offsetting, so rows inserted while
     * the user scrolls cannot shift the already-visible prefix.
     */
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
        WHERE (:accountId IS NULL OR transactions.accountId = :accountId)
          AND (:from IS NULL OR transactions.operationDate >= :from)
          AND (:to IS NULL OR transactions.operationDate <= :to)
          AND transactions.type NOT IN ('OPENING_BALANCE', 'INITIAL_BALANCE')
        ORDER BY transactions.operationDate DESC, transactions.operationAt DESC, transactions.createdAt DESC
        LIMIT :limit
        """,
    )
    fun getCategorizedWindow(
        accountId: String?,
        from: String?,
        to: String?,
        limit: Int,
    ): Flow<List<CategorizedTransactionEntity>>

    /**
     * Per-type, per-currency magnitude sums over the whole window described by
     * [getCategorizedWindow] — unaffected by that query's `LIMIT`, so a summary built from these
     * stays correct while pages load.
     *
     * Also grouped by the amount's sign: the legacy `REGULAR` type is resolved to income/expense
     * from the sign (see `TransactionRepositoryImpl.parseType`), which is only possible if rows
     * of either sign are not already summed together.
     */
    @Query(
        """
        SELECT
            transactions.type AS type,
            transactions.currencyCode AS currencyCode,
            (transactions.amount < 0) AS isNegative,
            SUM(ABS(transactions.amount)) AS total
        FROM transactions
        WHERE (:accountId IS NULL OR transactions.accountId = :accountId)
          AND (:from IS NULL OR transactions.operationDate >= :from)
          AND (:to IS NULL OR transactions.operationDate <= :to)
          AND transactions.type NOT IN ('OPENING_BALANCE', 'INITIAL_BALANCE')
        GROUP BY transactions.type, transactions.currencyCode, isNegative
        """,
    )
    fun getTotals(
        accountId: String?,
        from: String?,
        to: String?,
    ): Flow<List<TransactionTotalEntity>>

    @Query(
        "SELECT * FROM transactions WHERE workspaceId = :workspaceId ORDER BY operationDate DESC, operationAt DESC, createdAt DESC",
    )
    fun getByWorkspaceId(workspaceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    /**
     * Both legs of one transfer, oldest first so the pair reads source-then-destination for
     * legs saved in that order.
     *
     * Deliberately unindexed: `transferId` is null on the overwhelming majority of rows, this
     * runs once when a transfer's details screen opens, and an index would cost a schema
     * migration plus write amplification on every insert to serve that one lookup.
     */
    @Query("SELECT * FROM transactions WHERE transferId = :transferId ORDER BY createdAt ASC")
    suspend fun getByTransferId(transferId: String): List<TransactionEntity>

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
