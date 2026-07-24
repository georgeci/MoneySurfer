package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.AccountBalanceSeries
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.buildAccountBalanceSeries
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * Daily balance curve for one account over the trailing [days], for the account details chart.
 *
 * Takes the history the caller already holds instead of querying for it: the account details
 * screen collects the account's transactions for its quick stats, and a second flow would fetch
 * the same rows only to risk rendering a chart from a different snapshot than the totals beside
 * it. All this use case owns is "today" and the fold.
 */
@Single
class GetAccountBalanceSeriesUseCase(private val clock: ClockUseCase) {

    operator fun invoke(
        accountId: AccountId,
        currentBalance: Money,
        transactions: List<Transaction>,
        days: Int = AccountBalanceSeries.DEFAULT_DAYS,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): AccountBalanceSeries = buildAccountBalanceSeries(
        accountId = accountId,
        currentBalance = currentBalance,
        transactions = transactions,
        today = clock.now().toLocalDateTime(timeZone).date,
        days = days,
    )
}
