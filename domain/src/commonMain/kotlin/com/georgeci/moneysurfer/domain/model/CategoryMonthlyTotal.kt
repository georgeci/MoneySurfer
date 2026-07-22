package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.YearMonth

/**
 * What one category booked in one month, straight off the aggregate query.
 *
 * Deliberately flat and un-rolled-up: a row exists per *stored* `categoryId`, so a parent and
 * each of its children arrive separately and the caller decides how to combine them. Rolling
 * up in SQL would force a second query the moment a screen wants the per-child split, which
 * the subcategory breakdown does.
 *
 * [total] is a magnitude — the query sums `ABS(amount)` over a single transaction type, so an
 * expense category reads as a positive number rather than a negative one.
 */
data class CategoryMonthlyTotal(
    val categoryId: CategoryId,
    val month: YearMonth,
    val total: Money,
    val transactionCount: Int,
)
