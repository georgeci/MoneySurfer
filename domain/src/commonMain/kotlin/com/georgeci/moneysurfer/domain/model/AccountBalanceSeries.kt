package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Closing balance of one account at the end of [date]. */
data class AccountBalancePoint(
    val date: LocalDate,
    val balance: Money,
)

/**
 * The balance curve one account drew over a window, oldest point first, plus the net change
 * across that window.
 *
 * [delta] is not `last - first`: the first point already includes its own day's transactions,
 * so the difference between the ends would silently drop day one. It is the balance at the end
 * of the window minus the balance the account closed the day *before* it opened.
 */
data class AccountBalanceSeries(
    val points: List<AccountBalancePoint>,
    val delta: Money,
) {
    companion object {
        /** Window the account details chart draws, matching its "Balance · 30 days" label. */
        const val DEFAULT_DAYS: Int = 30
    }
}

/**
 * Rebuilds the daily balance curve by walking [transactions] backwards from [currentBalance].
 *
 * Anchored on the cached balance rather than summed from zero: the account's balance is the
 * authoritative figure the rest of the app shows, and a series summed independently would be
 * free to disagree with the number printed right above the chart. Working backwards makes the
 * last point equal that balance by construction.
 *
 * Pure. [transactions] may span any range and may contain rows for other accounts or non-ACTUAL
 * rows — [balanceImpact] filters those out. Rows dated after [today] are backed out of the
 * anchor, since the cached balance counts them but the curve must not.
 */
fun buildAccountBalanceSeries(
    accountId: AccountId,
    currentBalance: Money,
    transactions: List<Transaction>,
    today: LocalDate,
    days: Int = AccountBalanceSeries.DEFAULT_DAYS,
): AccountBalanceSeries {
    require(days > 0) { "days must be positive, was $days" }

    val byDate = mutableMapOf<LocalDate, Money>()
    var future = Money.zero()
    for (transaction in transactions) {
        val impact = transaction.balanceImpact()[accountId] ?: continue
        val date = transaction.operationDate
        if (date > today) {
            future += impact
        } else {
            byDate[date] = (byDate[date] ?: Money.zero()) + impact
        }
    }

    var running = currentBalance - future
    val points = ArrayList<AccountBalancePoint>(days)
    repeat(days) { index ->
        val date = today.minus(index, DateTimeUnit.DAY)
        points += AccountBalancePoint(date = date, balance = running)
        running -= byDate[date] ?: Money.zero()
    }
    points.reverse()

    // `running` has now walked past the oldest point, so it holds the closing balance of the
    // day before the window — the baseline the period delta is measured against.
    return AccountBalanceSeries(points = points, delta = points.last().balance - running)
}
