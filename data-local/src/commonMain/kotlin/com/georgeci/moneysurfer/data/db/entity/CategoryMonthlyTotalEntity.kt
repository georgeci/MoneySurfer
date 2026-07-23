package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo

/**
 * Projection returned by the per-month category aggregate query. Not a table — Room maps a
 * `GROUP BY` result onto it, so there is no `@Entity` and no schema footprint.
 *
 * [month] is an ISO `YYYY-MM` string, sliced straight out of `operationDate`. Slicing beats
 * re-deriving the month from `operationAt` because `operationDate` is the business date the
 * user assigned the transaction to — the same date budgets count it under.
 */
data class CategoryMonthlyTotalEntity(
    @ColumnInfo(name = "categoryId") val categoryId: String,
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "totalMinor") val totalMinor: Long,
    @ColumnInfo(name = "transactionCount") val transactionCount: Int,
)
