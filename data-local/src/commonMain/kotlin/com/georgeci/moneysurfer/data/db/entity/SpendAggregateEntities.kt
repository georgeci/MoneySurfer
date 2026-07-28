package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo

/*
 * Projections returned by the spend aggregate queries in TransactionDao. None of them is a table
 * — Room maps a `GROUP BY` result onto each, so there is no `@Entity` and no schema footprint.
 *
 * They stay one file because they exist only as the shape of five sibling queries; splitting them
 * would spread one decision across five files without adding a seam anywhere.
 *
 * Dates arrive as the raw column text (`YYYY-MM-DD`, or `YYYY-MM` sliced out of it). Parsing is
 * the repository's job: a string the domain cannot read must drop one row, not fail a screen.
 */

/** Spend for one category inside the window, or for the uncategorized bucket. */
data class CategorySpendSliceEntity(
    /** Null for the uncategorized bucket — `transactions.categoryId` is nullable. */
    @ColumnInfo(name = "categoryId") val categoryId: String?,
    @ColumnInfo(name = "totalMinor") val totalMinor: Long,
    @ColumnInfo(name = "transactionCount") val transactionCount: Int,
)

/** One `(month, type)` cell of the income-vs-expense trend; the two types arrive as two rows. */
data class MonthlyTypeTotalEntity(
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "totalMinor") val totalMinor: Long,
)

data class DailySpendEntity(
    @ColumnInfo(name = "operationDate") val operationDate: String,
    @ColumnInfo(name = "totalMinor") val totalMinor: Long,
)

data class MerchantSpendEntity(
    @ColumnInfo(name = "merchant") val merchant: String,
    @ColumnInfo(name = "totalMinor") val totalMinor: Long,
    @ColumnInfo(name = "transactionCount") val transactionCount: Int,
)

data class CurrencySpendEntity(
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    @ColumnInfo(name = "totalMinor") val totalMinor: Long,
)
