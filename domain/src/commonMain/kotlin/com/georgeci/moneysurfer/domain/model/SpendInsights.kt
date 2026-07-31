package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
import com.georgeci.moneysurfer.domain.util.shiftPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Which slice of the calendar the insights screen is read at: one of the cadences the dashboard
 * already offers, plus the period the user has paged to.
 *
 * **Not a second period type.** [mode] is the dashboard's own [DashboardPeriod] and [window]
 * delegates to `periodWindow`, so a screen scoped by this cuts the calendar at exactly the same
 * dates as the dashboard's widgets and the transactions list. What it adds is the [anchor], which
 * a dashboard widget has no use for: a widget only ever shows the period containing today, while a
 * screen with prev/next arrows has to remember *which* period it is showing.
 */
data class InsightsSelection(
    val mode: DashboardPeriod,
    val anchor: LocalDate,
) {

    /** Always bounded — neither [DashboardPeriod] entry is all-time. */
    val window: TransactionPeriodWindow get() = periodWindow(mode.mode, anchor)

    /** This selection moved [by] whole periods; negative goes back. */
    fun shifted(by: Int): InsightsSelection = copy(anchor = shiftPeriod(mode.mode, anchor, by))
}

/**
 * Everything the insights screen draws for one [InsightsSelection]: where the period's money went,
 * how income and expense have moved month by month, and who took the most.
 *
 * [breakdown] and [merchants] are scoped to [InsightsSelection.window]; [months] deliberately is
 * not — see [SpendInsights.MONTH_COLUMNS].
 *
 * [currency] is the workspace base currency, and on this screen it is a *filter* rather than a
 * formatting hint: everything else in the workspace is missing from the figures above and listed in
 * [excludedByCurrency] instead. That is what makes [hiddenByBaseCurrency] answerable — a screen that
 * only knew "no spend" could not tell an empty period from a workspace whose spending is all in
 * another currency.
 */
data class SpendInsights(
    val selection: InsightsSelection,
    val currency: CurrencyCode,
    val breakdown: SpentByCategory,
    /** One entry per column, oldest first, gaps filled — always [MONTH_COLUMNS] long. */
    val months: List<MonthlyNet>,
    /** Largest first, at most [TOP_MERCHANTS] entries. */
    val merchants: List<MerchantSpend>,
    /** Per-currency spend the [currency] filter left out, largest first. */
    val excludedByCurrency: List<CurrencyTotal>,
) {

    /**
     * Whether the base-currency filter is the reason the period looks empty.
     *
     * The screen has to say so rather than draw a blank donut: in a workspace whose cards are all
     * in another currency, silence reads as a bug instead of as the policy it is (`md/insights.md`
     * decision 4). Nothing else on the screen can distinguish the two cases — the aggregates
     * already applied the filter, so what it removed is only visible here.
     */
    val hiddenByBaseCurrency: Boolean
        get() = breakdown.entries.isEmpty() && excludedByCurrency.isNotEmpty()

    companion object {
        /**
         * Columns the income-vs-expense chart draws, ending at the month the selection is anchored
         * in. Six for the same reason [CategorySpendHistory.TREND_MONTHS] is six, halved again by
         * this chart drawing two bars per column.
         *
         * The chart spans months whichever cadence [InsightsSelection.mode] names, because
         * `netByMonth` groups *inside* the window it is given and a week-wide window would come back
         * as one part-month column under a full month's name. A week of spend is what the breakdown
         * and the merchant list answer for.
         */
        const val MONTH_COLUMNS: Int = 6

        /** Rows the merchant list draws. Enough to see a pattern, few enough to read at a glance. */
        const val TOP_MERCHANTS: Int = 8
    }
}

/**
 * Fills [rows] out to one entry per month in [months], oldest first.
 *
 * `netByMonth` only returns months the workspace booked something in, so a chart drawn straight off
 * it would silently shrink to however many months had activity — a quiet half-year would draw a
 * two-column chart labelled as six months. Same tolerance [buildCategorySpendHistory] applies to
 * the category trend.
 *
 * Rows outside [months] are dropped rather than trusted, so a caller passing a wider query result
 * cannot add a column the chart never asked for.
 */
fun buildNetTrend(months: List<YearMonth>, rows: List<MonthlyNet>): List<MonthlyNet> {
    val byMonth = rows.associateBy { it.month }
    return months.map { month ->
        byMonth[month] ?: MonthlyNet(month = month, income = Money.zero(), expense = Money.zero())
    }
}
