package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.YearMonth

/** What the workspace closed [month] holding, in the base currency. */
data class BalanceTrendPoint(
    val month: YearMonth,
    val balance: Money,
)

/**
 * The curve the total balance drew over the trailing months, oldest point first, plus what the
 * newest month has changed it by.
 *
 * Monthly rather than daily, because a month is the coarsest grain the app can answer without a
 * new aggregate: [MonthlyNet] is the one rollup that counts income as well as spend, and
 * `SpendAnalyticsRepository.daily` is expenses-only, so a daily balance curve would need a query
 * that does not exist. Six points still show the shape the sparkline is there for.
 *
 * [delta] is the newest month's net, which is the same figure as "balance now minus what the
 * workspace closed last month on" — the two ends of the curve, without the rounding a subtraction
 * of two displayed figures would invite.
 */
data class BalanceTrend(
    val points: List<BalanceTrendPoint>,
    val delta: Money,
) {
    companion object {
        /** Months the sparkline draws. Six matches the category trend's window. */
        const val TREND_MONTHS: Int = 6

        val Empty = BalanceTrend(points = emptyList(), delta = Money.zero())
    }
}

/**
 * The months a balance trend is folded over, the base-currency net each of them booked, and the
 * currency all of it is quoted in.
 *
 * Carries its own [currency] because it is a *filter* on the query behind [nets], not a formatting
 * hint — see [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository]. A caller
 * anchoring the fold on a total priced in some other currency has to refuse the pairing rather
 * than fold euros into dollars.
 */
data class MonthlyNetHistory(
    val months: List<YearMonth>,
    val nets: List<MonthlyNet>,
    val currency: CurrencyCode,
)

/**
 * Walks [nets] backwards from [currentBalance] to rebuild what the workspace closed each of
 * [months] holding.
 *
 * Anchored on the balance the dashboard is already printing rather than summed forward from zero,
 * for the same reason [buildAccountBalanceSeries] is: the total is the authoritative figure, and a
 * curve summed independently would be free to end somewhere else than the headline above it.
 * Working backwards makes the newest point equal that total by construction.
 *
 * Pure, and tolerant of a sparse [nets] — the aggregate only returns months that booked something,
 * so a quiet month folds to zero here instead of dropping a point. Rows outside [months] are
 * ignored rather than trusted, so a caller passing a wider query result cannot bend the curve.
 *
 * What the curve cannot see: opening balances and transfers are outside [MonthlyNet] by
 * construction, and so is anything booked in another currency. An account opened inside the window
 * therefore reads as having always held its opening balance. That is a shape the sparkline may
 * overstate; [BalanceTrend.delta] is unaffected, being this month's income minus its spend and
 * nothing else.
 */
fun buildBalanceTrend(
    currentBalance: Money,
    nets: List<MonthlyNet>,
    months: List<YearMonth>,
): BalanceTrend {
    if (months.isEmpty()) return BalanceTrend.Empty

    val netByMonth = nets.groupBy { it.month }
        .mapValues { (_, rows) -> rows.fold(Money.zero()) { acc, row -> acc + row.net } }

    var running = currentBalance
    val points = ArrayList<BalanceTrendPoint>(months.size)
    for (month in months.asReversed()) {
        points += BalanceTrendPoint(month = month, balance = running)
        running -= netByMonth[month] ?: Money.zero()
    }
    points.reverse()

    return BalanceTrend(
        points = points,
        delta = netByMonth[months.last()] ?: Money.zero(),
    )
}
