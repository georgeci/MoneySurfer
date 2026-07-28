package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/*
 * Return shapes of SpendAnalyticsRepository.
 *
 * All of them are magnitudes in the workspace base currency: the queries behind them sum
 * `ABS(amount)`, so an expense reads as a positive number rather than a negative one. Nothing
 * here is persisted or pushed — a change to the maths never needs a migration.
 */

/**
 * What one category booked inside a spend window.
 *
 * [categoryId] is `null` for the uncategorized bucket. That is a real slice, not a missing one:
 * `Transaction.categoryId` is nullable, and a workspace with half its rows uncategorized would
 * otherwise show a donut that does not add up to what was spent.
 */
data class CategorySpendSlice(
    val categoryId: CategoryId?,
    val total: Money,
    val transactionCount: Int,
)

/**
 * One month of the income-vs-expense trend. Both figures are magnitudes, so [net] is the
 * subtraction rather than a sum of signed rows.
 *
 * A month with income but no spend still produces a row — the query groups by month and type,
 * and the missing side folds to zero here rather than dropping the column from the chart.
 *
 * [month] names the calendar month the rows fell in; it does *not* promise the whole month was
 * counted. A query window that starts or ends mid-month yields a part-month row indistinguishable
 * from a full one — see
 * [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository.netByMonth].
 */
data class MonthlyNet(
    val month: YearMonth,
    val income: Money,
    val expense: Money,
) {
    /** Negative once the month spent more than it earned. */
    val net: Money get() = income - expense
}

/**
 * One day of the spend series. Days with nothing booked are absent — the heatmap and the burn
 * rate both need to tell "no spend" from "no data", and only the caller knows which window it
 * asked for.
 */
data class DailySpendPoint(
    val date: LocalDate,
    val total: Money,
)

/**
 * What one merchant took inside a spend window. [merchant] is never blank — see
 * [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository.topMerchants].
 */
data class MerchantSpend(
    val merchant: String,
    val total: Money,
    val transactionCount: Int,
)
