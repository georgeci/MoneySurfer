package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How many days of spend the burn-rate chart draws, ending on the day it was built for. */
const val BURN_RATE_DAYS: Int = 7

/**
 * The last [BURN_RATE_DAYS] days of spend, plus the month-to-date total the projection starts from.
 *
 * Every figure is a magnitude in [currency] — the queries behind
 * [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository] sum `ABS(amount)`, so an
 * expense reads positive and nothing here is ever negative.
 *
 * [days] is always [BURN_RATE_DAYS] long and ends on [today]: the repository leaves out days
 * nothing was booked on, and a chart that skipped those would silently redraw a quiet week as a
 * busy one. Filling them with zero is what makes the bars comparable.
 *
 * **[today] is a partial day.** Its bar and its share of [average] only cover the hours already
 * logged, which drags the average down a little every morning. Counting it anyway is the deliberate
 * trade: a chart that omitted the transaction the user logged ten minutes ago would read as a bug,
 * and the distortion is one seventh of one day.
 */
data class DailySpendSeries(
    val days: List<DailySpendPoint>,
    /** Spend booked from the 1st of [today]'s month through [today], today included. */
    val monthToDate: Money,
    /** The last day of [days] — the day the projection is made from. */
    val today: LocalDate,
    val currency: CurrencyCode,
    /**
     * The workspace these figures were queried for. Carried rather than assumed so anything pairing
     * the series with a second per-workspace query can check the two agree — see [monthlySpendCap].
     */
    val workspaceId: WorkspaceId,
) {

    /** What the whole window booked. */
    val total: Money get() = days.fold(Money.zero()) { sum, point -> sum + point.total }

    /**
     * Mean daily spend across the window. Integer division, so it truncates to the minor unit —
     * a projection built from it lands a few cents low rather than inventing a fraction of a cent.
     */
    val average: Money get() = total / BURN_RATE_DAYS

    /**
     * Each day's bar height against the tallest day of the window, index-aligned with [days].
     *
     * Scaled to the peak rather than to the budget: the chart's job is the *shape* of the week, and
     * a week that spent a tenth of the cap would otherwise be seven invisible slivers. All zero when
     * the window booked nothing, which the chart draws as a flat baseline rather than a NaN.
     */
    val barFractions: List<Float>
        get() {
            val peak = days.maxOfOrNull { it.total.minor } ?: 0L
            if (peak <= 0L) return List(days.size) { 0f }
            return days.map { (it.total.minor.toFloat() / peak.toFloat()).coerceIn(0f, 1f) }
        }

    /**
     * Days of [today]'s month still ahead of it. Today is excluded because its spend is already
     * inside [monthToDate] — projecting it again would count one day twice.
     */
    val daysAheadInMonth: Int
        get() = lastDayOfMonth(today).day - today.day
}

/** Whether the projected month-end total lands inside the budget. */
enum class BurnRatePace {
    OnTrack,
    OffPace,
}

/**
 * Spend pace for the dashboard: what the last week actually cost per day, and where the month ends
 * up if it keeps costing that.
 *
 * [monthlyLimit] is optional by design — the widget's whole first half (the week, the average, the
 * projection) is arithmetic over transactions, and a workspace with no budget still deserves to see
 * it. Only [pace] needs a cap to mean anything.
 */
data class BurnRate(
    val series: DailySpendSeries,
    /**
     * What [DailySpendSeries.monthToDate] grows to if the remaining days of the month each cost
     * [DailySpendSeries.average]. An estimate, not a forecast — no seasonality, no scheduled bills.
     */
    val projectedMonthTotal: Money,
    /** The monthly cap [projectedMonthTotal] is measured against, or null when no budget sets one. */
    val monthlyLimit: Money?,
) {

    val currency: CurrencyCode get() = series.currency

    /** Null without a [monthlyLimit] — the widget then draws the projection with no verdict on it. */
    val pace: BurnRatePace?
        get() = monthlyLimit?.let {
            if (projectedMonthTotal <= it) BurnRatePace.OnTrack else BurnRatePace.OffPace
        }
}

/**
 * The window a [DailySpendSeries] has to be queried over: enough days for the chart, and back to
 * the 1st for the month-to-date.
 *
 * One window rather than two queries — early in the month the chart reaches back into the previous
 * one, and later in the month the 1st is the earlier bound, so whichever is further back wins.
 */
fun dailySpendSeriesWindow(today: LocalDate): TransactionPeriodWindow = TransactionPeriodWindow(
    from = minOf(firstDayOfMonth(today), today.minus(BURN_RATE_DAYS - 1, DateTimeUnit.DAY)),
    to = today,
)

/**
 * [points] folded into the series the widget draws, filling the days the query left out.
 *
 * Points outside the queried window are ignored rather than trusted: [monthToDate] counts only
 * [today]'s own month, so the days [dailySpendSeriesWindow] reaches back into the previous month for
 * cannot leak into it.
 */
fun dailySpendSeries(
    points: List<DailySpendPoint>,
    today: LocalDate,
    currency: CurrencyCode,
    workspaceId: WorkspaceId,
): DailySpendSeries {
    val byDate = points.associate { it.date to it.total }
    val firstDay = today.minus(BURN_RATE_DAYS - 1, DateTimeUnit.DAY)
    val days = List(BURN_RATE_DAYS) { offset ->
        val date = firstDay.plus(offset, DateTimeUnit.DAY)
        DailySpendPoint(date = date, total = byDate[date] ?: Money.zero())
    }
    val monthStart = firstDayOfMonth(today)
    val monthToDate = points
        .filter { it.date >= monthStart && it.date <= today }
        .fold(Money.zero()) { sum, point -> sum + point.total }
    return DailySpendSeries(
        days = days,
        monthToDate = monthToDate,
        today = today,
        currency = currency,
        workspaceId = workspaceId,
    )
}

/** The projection [monthlyLimit] is judged against; pass null when no budget backs a cap. */
fun DailySpendSeries.project(monthlyLimit: Money?): BurnRate = BurnRate(
    series = this,
    projectedMonthTotal = monthToDate + average * daysAheadInMonth,
    monthlyLimit = monthlyLimit,
)

/**
 * The cap [workspaceId]'s projected month can honestly be read against, or null when no budget sets
 * one.
 *
 * Three filters do the work. Two are about comparing like with like:
 *
 * - **monthly periods only.** The projection covers a calendar month, so a weekly or yearly limit is
 *   simply a different quantity. Scaling one into a monthly equivalent would invent a number the
 *   user never set.
 * - **general budgets only** — no category filter, so they cover every expense, which is exactly
 *   what the projection sums. A category cap covers a slice of the spend; measuring the whole
 *   month's projection against it would report "off pace" on a budget that is nowhere near its cap.
 *
 * The third is about not comparing two workspaces. [workspaceId] is required rather than assumed
 * because the caller pairs this list with a spend series read through a *second* subscription to
 * `SessionPointers.currentWorkspaceId`, and two collectors of that pointer are not ordered against
 * each other: on a workspace switch the budget query can already be answering for the new workspace
 * while the series is still the old one. That is the hazard [safeToSpend] avoids by taking its
 * workspace from the budgets themselves — an option here only if a burn rate needed a budget to
 * exist at all, which it does not. Filtering instead makes a mismatched pair yield no cap, so the
 * widget drops the verdict for a frame rather than judging one workspace's spend by another's limit.
 *
 * The largest qualifying limit wins. Ties need no further tiebreak: the answer is the amount, so two
 * budgets capped the same are the same answer.
 *
 * [Budget.amount] rather than an effective limit with a rollover carry: a carry belongs to one
 * budget window, and this is measured against a calendar month, which a budget anchored mid-month
 * does not line up with. The plain amount is the per-month cap the user set, whichever day it runs
 * from.
 */
fun List<Budget>.monthlySpendCap(workspaceId: WorkspaceId): Money? =
    filter {
        it.workspaceId == workspaceId &&
            it.isActive &&
            it.period == BudgetPeriod.MONTHLY &&
            it.categoryIds.isEmpty()
    }.maxOfOrNull { it.amount }

private fun firstDayOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

private fun lastDayOfMonth(date: LocalDate): LocalDate =
    firstDayOfMonth(date).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
