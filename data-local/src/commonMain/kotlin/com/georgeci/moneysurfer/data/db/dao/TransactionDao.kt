package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.CategorizedTransactionEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryMonthlyTotalEntity
import com.georgeci.moneysurfer.data.db.entity.CategorySpendSliceEntity
import com.georgeci.moneysurfer.data.db.entity.CurrencySpendEntity
import com.georgeci.moneysurfer.data.db.entity.DailySpendEntity
import com.georgeci.moneysurfer.data.db.entity.MerchantSpendEntity
import com.georgeci.moneysurfer.data.db.entity.MonthlyTypeTotalEntity
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
     *
     * The transfer, currency and canonical-date terms come from the shared spend predicate
     * documented over [getSpendByCategory]. This query used to sum transfer legs and add minor
     * units across currencies, which made the category trend disagree with both the budget screen
     * and the Insights aggregates for the same month. A null [baseCurrency] — a workspace that
     * could not be read — matches nothing, exactly as `Budget.counts` does.
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
            AND transactions.transferId IS NULL
            AND transactions.currencyCode = :baseCurrency
            AND transactions.operationDate = date(transactions.operationDate)
            AND transactions.operationDate >= :fromDate
            AND transactions.operationDate < :toDateExclusive
        GROUP BY transactions.categoryId, month
        """,
    )
    fun getMonthlyTotalsByCategory(
        workspaceId: String,
        categoryIds: List<String>,
        type: String,
        baseCurrency: String?,
        fromDate: String,
        toDateExclusive: String,
    ): Flow<List<CategoryMonthlyTotalEntity>>

    // region Spend aggregates
    //
    // The five queries below back SpendAnalyticsRepository, and all of them apply the same
    // predicate — the one budgets already use. Written out once here so a sixth query cannot
    // quietly drift from it; Room needs the SQL inline, so the copies are deliberate:
    //
    //   WHERE workspaceId = :workspaceId
    //     AND type = 'EXPENSE' AND status = 'ACTUAL'
    //     AND transferId IS NULL
    //     AND currencyCode = :baseCurrency
    //     AND operationDate = date(operationDate)
    //     AND operationDate >= :fromDate AND operationDate < :toDateExclusive
    //
    // Term by term:
    //
    // - `type = 'EXPENSE'` — the stored spelling, not the parsed one. The legacy `REGULAR` type
    //   that `TransactionRepositoryImpl.parseType` resolves by sign is not counted here, matching
    //   getMonthlyTotalsByCategory; a device still holding such rows sees them nowhere in
    //   Insights. getNetTotalsByMonth widens this term to `IN ('EXPENSE', 'INCOME')` because it
    //   needs both sides, and splits them again by grouping on the column.
    // - `transferId IS NULL` — transfer legs carry a CategoryType.TRANSFER category, so filtering
    //   by category type would not catch them.
    // - `currencyCode = :baseCurrency` — v1 converts nothing; getSpendByExcludedCurrency reports
    //   what this term left behind. A null base currency matches no row.
    // - Bounds are nullable so an all-time window needs no second query shape, half-open at the
    //   top so the caller never has to know how long the final month is.
    // - `operationDate = date(operationDate)` admits only the canonical `YYYY-MM-DD` spelling.
    //   SQLite's `date()` returns NULL for text it cannot read and a normalized date for text it
    //   can, so the comparison rejects `''`, `'2025-3-5'`, `'2025-03-05T10:00'` and a Julian day
    //   number alike — every string `LocalDate.parse` would also refuse. Without it the month and
    //   day series (which parse) and the category, merchant and currency rollups (which do not)
    //   disagree by exactly those rows. `''` is the only spelling seen in the wild, from before
    //   the column existed, and MIGRATION_27_28 already backfilled it; the rest is a guard against
    //   an older or foreign writer. A bounded window mostly excludes these by string comparison
    //   anyway — this makes it true for every window, which is the point of one shared predicate.
    //
    // The composite `(workspaceId, operationDate DESC, operationAt DESC, createdAt DESC)` index
    // covers all five. `merchant` is unindexed, so getTopMerchants scans the window — acceptable
    // while the window is bounded.

    /**
     * Spend per category inside the window, largest first. `categoryId` comes back null for the
     * uncategorized bucket, which is a slice of the result rather than a gap in it.
     */
    @Query(
        """
        SELECT
            transactions.categoryId AS categoryId,
            SUM(ABS(transactions.amount)) AS totalMinor,
            COUNT(*) AS transactionCount
        FROM transactions
        WHERE transactions.workspaceId = :workspaceId
            AND transactions.type = 'EXPENSE'
            AND transactions.status = 'ACTUAL'
            AND transactions.transferId IS NULL
            AND transactions.currencyCode = :baseCurrency
            AND transactions.operationDate = date(transactions.operationDate)
            AND (:fromDate IS NULL OR transactions.operationDate >= :fromDate)
            AND (:toDateExclusive IS NULL OR transactions.operationDate < :toDateExclusive)
        GROUP BY transactions.categoryId
        ORDER BY totalMinor DESC, transactions.categoryId ASC
        """,
    )
    fun getSpendByCategory(
        workspaceId: String,
        baseCurrency: String,
        fromDate: String?,
        toDateExclusive: String?,
    ): Flow<List<CategorySpendSliceEntity>>

    /**
     * Per-month, per-type magnitudes inside the window, oldest month first — the income-vs-expense
     * trend.
     *
     * The one aggregate that widens the type term instead of dropping it: income belongs in the
     * trend, an opening balance is an account artefact and would otherwise conjure up a month out
     * of nothing but the day an account was opened. Grouping by type in SQL rather than summing
     * signed amounts keeps both sides available — a month's net is a subtraction the caller can
     * make, but a net stored as one number cannot be split back apart.
     */
    @Query(
        """
        SELECT
            substr(transactions.operationDate, 1, 7) AS month,
            transactions.type AS type,
            SUM(ABS(transactions.amount)) AS totalMinor
        FROM transactions
        WHERE transactions.workspaceId = :workspaceId
            AND transactions.type IN ('EXPENSE', 'INCOME')
            AND transactions.status = 'ACTUAL'
            AND transactions.transferId IS NULL
            AND transactions.currencyCode = :baseCurrency
            AND transactions.operationDate = date(transactions.operationDate)
            AND (:fromDate IS NULL OR transactions.operationDate >= :fromDate)
            AND (:toDateExclusive IS NULL OR transactions.operationDate < :toDateExclusive)
        GROUP BY month, transactions.type
        ORDER BY month ASC
        """,
    )
    fun getNetTotalsByMonth(
        workspaceId: String,
        baseCurrency: String,
        fromDate: String?,
        toDateExclusive: String?,
    ): Flow<List<MonthlyTypeTotalEntity>>

    /** Spend per business date inside the window, oldest first. Days without spend are absent. */
    @Query(
        """
        SELECT
            transactions.operationDate AS operationDate,
            SUM(ABS(transactions.amount)) AS totalMinor
        FROM transactions
        WHERE transactions.workspaceId = :workspaceId
            AND transactions.type = 'EXPENSE'
            AND transactions.status = 'ACTUAL'
            AND transactions.transferId IS NULL
            AND transactions.currencyCode = :baseCurrency
            AND transactions.operationDate = date(transactions.operationDate)
            AND (:fromDate IS NULL OR transactions.operationDate >= :fromDate)
            AND (:toDateExclusive IS NULL OR transactions.operationDate < :toDateExclusive)
        GROUP BY transactions.operationDate
        ORDER BY transactions.operationDate ASC
        """,
    )
    fun getDailySpend(
        workspaceId: String,
        baseCurrency: String,
        fromDate: String?,
        toDateExclusive: String?,
    ): Flow<List<DailySpendEntity>>

    /**
     * The [limit] biggest merchants inside the window, largest first.
     *
     * Rows with no merchant are excluded rather than bucketed: the column defaults to `''`, and an
     * empty label is not a counterparty the user could act on — it would just be the largest bar
     * in most workspaces. Uncategorized spend is still reachable through [getSpendByCategory].
     */
    @Query(
        """
        SELECT
            transactions.merchant AS merchant,
            SUM(ABS(transactions.amount)) AS totalMinor,
            COUNT(*) AS transactionCount
        FROM transactions
        WHERE transactions.workspaceId = :workspaceId
            AND transactions.type = 'EXPENSE'
            AND transactions.status = 'ACTUAL'
            AND transactions.transferId IS NULL
            AND transactions.currencyCode = :baseCurrency
            AND transactions.merchant <> ''
            AND transactions.operationDate = date(transactions.operationDate)
            AND (:fromDate IS NULL OR transactions.operationDate >= :fromDate)
            AND (:toDateExclusive IS NULL OR transactions.operationDate < :toDateExclusive)
        GROUP BY transactions.merchant
        ORDER BY totalMinor DESC, transactions.merchant ASC
        LIMIT :limit
        """,
    )
    fun getTopMerchants(
        workspaceId: String,
        baseCurrency: String,
        fromDate: String?,
        toDateExclusive: String?,
        limit: Int,
    ): Flow<List<MerchantSpendEntity>>

    /**
     * Spend the base-currency term left out, per currency, largest first — the inverse of the
     * other four, so no expense in the window goes unreported.
     */
    @Query(
        """
        SELECT
            transactions.currencyCode AS currencyCode,
            SUM(ABS(transactions.amount)) AS totalMinor
        FROM transactions
        WHERE transactions.workspaceId = :workspaceId
            AND transactions.type = 'EXPENSE'
            AND transactions.status = 'ACTUAL'
            AND transactions.transferId IS NULL
            AND transactions.currencyCode <> :baseCurrency
            AND transactions.operationDate = date(transactions.operationDate)
            AND (:fromDate IS NULL OR transactions.operationDate >= :fromDate)
            AND (:toDateExclusive IS NULL OR transactions.operationDate < :toDateExclusive)
        GROUP BY transactions.currencyCode
        ORDER BY totalMinor DESC, transactions.currencyCode ASC
        """,
    )
    fun getSpendByExcludedCurrency(
        workspaceId: String,
        baseCurrency: String,
        fromDate: String?,
        toDateExclusive: String?,
    ): Flow<List<CurrencySpendEntity>>
    // endregion

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
