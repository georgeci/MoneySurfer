package com.georgeci.moneysurfer.domain.util

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.datetime.yearsUntil

private const val DAYS_PER_WEEK = 7

/**
 * A single half-open budget period: `[startInclusive, endExclusive)`.
 *
 * Half-open so consecutive windows tile the timeline without overlapping —
 * a transaction on a boundary date belongs to exactly one window.
 */
data class BudgetPeriodWindow(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date >= startInclusive && date < endExclusive
}

/**
 * The window that contains [today]. Pure — the caller supplies the date, so there is
 * no `Clock` dependency and the maths stays trivially testable.
 */
fun Budget.currentWindow(today: LocalDate): BudgetPeriodWindow = windowContaining(today)

/**
 * The window containing [date], counting whole periods from [Budget.startDate].
 *
 * Windows extend backwards before `startDate` too — [previousWindow] relies on that to
 * compute a rollover carry for the very first period.
 */
fun Budget.windowContaining(date: LocalDate): BudgetPeriodWindow {
    var index = estimatedIndexOf(date)
    while (anchorAt(index) > date) index--
    while (anchorAt(index + 1) <= date) index++
    return BudgetPeriodWindow(anchorAt(index), anchorAt(index + 1))
}

/** The window immediately preceding [window] — the source of a rollover carry. */
fun Budget.previousWindow(window: BudgetPeriodWindow): BudgetPeriodWindow =
    windowContaining(window.startInclusive.minus(1, DateTimeUnit.DAY))

/**
 * The period boundary [index] whole periods after [Budget.startDate], negative index
 * counting backwards.
 *
 * Every anchor is derived from `startDate` itself rather than from the previous anchor, so
 * short-month clamping never accumulates: a budget anchored on the 31st yields Jan 31 →
 * Feb 28 → Mar 31, not Jan 31 → Feb 28 → Mar 28. `LocalDate.plus` clamps a month/year step
 * to the last valid day of the target month, which is exactly the anchoring rule we want
 * (including Feb 29 → Feb 28 for a yearly budget in a non-leap year).
 */
private fun Budget.anchorAt(index: Int): LocalDate = when (period) {
    BudgetPeriod.WEEKLY -> startDate.plus(index * DAYS_PER_WEEK, DateTimeUnit.DAY)
    BudgetPeriod.MONTHLY -> startDate.plus(index, DateTimeUnit.MONTH)
    BudgetPeriod.YEARLY -> startDate.plus(index, DateTimeUnit.YEAR)
}

/**
 * A cheap first guess at the index whose window holds [date]. Clamping and truncation make it
 * off by at most one period, which the correction loops in [windowContaining] walk off.
 */
private fun Budget.estimatedIndexOf(date: LocalDate): Int = when (period) {
    BudgetPeriod.WEEKLY -> startDate.daysUntil(date).floorDiv(DAYS_PER_WEEK)
    BudgetPeriod.MONTHLY -> startDate.monthsUntil(date)
    BudgetPeriod.YEARLY -> startDate.yearsUntil(date)
}
