package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the current month has cost so far, against the cap it was given and against the month
 * before it.
 *
 * All three numbers are magnitudes in [currency] — the base currency the aggregates were filtered
 * on, so nothing here mixes two currencies the way the old period-totals use case did.
 *
 * [previousSpent] is the *same stretch* of the previous month, not the whole of it: comparing 15
 * elapsed days against 30 would report a fall every month and a rise on no month at all. That is
 * the comparison [com.georgeci.moneysurfer.domain.insight] already makes ("against %s by the same
 * day last month"), and [previousSpentMonthWindow] is the window that produces it.
 */
data class SpentMonth(
    /**
     * The day the figures were read on. Carried rather than derived so the elapsed-day guard under
     * [delta] and the window the spend was queried for can never disagree about which day it is.
     */
    val today: LocalDate,
    /** Spend booked from the 1st of [today]'s month through [today] inclusive. */
    val spent: Money,
    /** Spend booked over the same stretch of the previous month. Zero when it booked nothing. */
    val previousSpent: Money,
    /**
     * The monthly cap the spend is measured against, or null when no budget sets one — the
     * widget's "no budget set" state. See [monthlySpendCap] for which budgets qualify.
     */
    val cap: Money?,
    val currency: CurrencyCode,
) {

    /** Days of the month gone, counting [today] — what [delta] holds its comparison against. */
    val elapsedDays: Int get() = today.day

    /**
     * Spend against [cap] — what a progress bar is drawn from — or null while nothing caps the
     * month, which is a bar the widget leaves empty rather than one it fills to an invented limit.
     *
     * Can exceed 1, which the bar clamps rather than overdrawing. A zero or negative cap reads as
     * 0 instead of a NaN or an infinity, the same guard [BudgetProgress.spentFraction] applies.
     */
    val capFraction: Float?
        get() = cap?.let {
            if (it.minor <= 0L) 0f else spent.minor.toFloat() / it.minor.toFloat()
        }

    /**
     * How this month is running against the last one, or null when the comparison would not be
     * honest:
     *
     * - **Before [MIN_COMPARISON_DAYS] have elapsed.** On the 2nd the stretch being compared is two
     *   days, and one bill landing a day either side of the month boundary swings the answer by
     *   100%. The insights engine stands its comparison rules down for exactly this reason, and
     *   #428 landed because a shorter-than-the-window baseline is worse than no baseline.
     * - **When [previousSpent] is not positive.** A month that booked nothing is not a baseline a
     *   percentage can be read against, and "up ∞%" is not a number to put on a card.
     */
    val delta: SpentMonthDelta?
        get() {
            if (elapsedDays < MIN_COMPARISON_DAYS || !previousSpent.isPositive()) return null
            val ratio = (spent - previousSpent).minor.toDouble() / previousSpent.minor.toDouble()
            return SpentMonthDelta(
                trend = when {
                    ratio >= SPENT_MONTH_CHANGE_RATIO -> SpendTrend.Up
                    ratio <= -SPENT_MONTH_CHANGE_RATIO -> SpendTrend.Down
                    else -> SpendTrend.Flat
                },
                changePercent = (abs(ratio) * PERCENT_SCALE).roundToInt(),
            )
        }

    companion object {
        /**
         * Elapsed days the month needs before the delta says anything. Mirrors the insights
         * engine's own guard — see [delta] for why a shorter stretch is dominated by the timing of
         * a single charge.
         */
        const val MIN_COMPARISON_DAYS: Int = 7
    }
}

/**
 * Which way the month is running and by how much. [changePercent] is a magnitude — the direction
 * is [trend], so the widget picks one sentence rather than printing a sign into another.
 */
data class SpentMonthDelta(
    val trend: SpendTrend,
    val changePercent: Int,
)

/**
 * The 1st of [today]'s month through [today] — the window [SpentMonth.spent] is read over.
 *
 * Deliberately month-to-date rather than the whole calendar month: the figure is the answer to
 * "what has this month cost", and a month's remaining days have not cost anything yet. It is also
 * what makes [previousSpentMonthWindow] a like-for-like baseline.
 */
fun spentMonthWindow(today: LocalDate): TransactionPeriodWindow =
    TransactionPeriodWindow(from = firstOfMonth(today), to = today)

/**
 * The same stretch of the previous month: its 1st through its [today]-th day.
 *
 * Clamped to the previous month's length, so the 31st of March compares against the whole of
 * February rather than overflowing into March — the closest same-stretch window a short month has.
 */
fun previousSpentMonthWindow(today: LocalDate): TransactionPeriodWindow {
    val start = firstOfMonth(today).minus(1, DateTimeUnit.MONTH)
    val lastDay = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    return TransactionPeriodWindow(
        from = start,
        to = if (today.day >= lastDay.day) lastDay else LocalDate(start.year, start.month, today.day),
    )
}

/**
 * Expense across [this], as a magnitude.
 *
 * Folded rather than taking the single row each spent-month window returns: `netByMonth` omits a
 * month it has nothing for, so an empty list has to read as zero spend, and a fold covers both
 * shapes without a null check that would then have to mean "no data" somewhere else.
 */
fun List<MonthlyNet>.expenseTotal(): Money =
    fold(Money.zero()) { running, month -> running + month.expense }

private fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

/**
 * How far the month has to have moved before the change is worth a label. A whole month's spend is
 * a steady number, so a tenth of it is already notable — the same call the insights engine's
 * period rule makes.
 */
private const val SPENT_MONTH_CHANGE_RATIO = 0.10

private const val PERCENT_SCALE = 100
