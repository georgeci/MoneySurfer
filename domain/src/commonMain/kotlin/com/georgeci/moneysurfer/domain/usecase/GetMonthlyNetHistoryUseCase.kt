package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.BalanceTrend
import com.georgeci.moneysurfer.domain.model.MonthlyNetHistory
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.trailingMonths
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.core.annotation.Single

/**
 * Income against spend per calendar month over the trailing [BalanceTrend.TREND_MONTHS], for the
 * balance widget's sparkline and its month delta. Null while nothing backs it — nobody signed in,
 * no workspace selected, or a workspace row the device has not pulled yet.
 *
 * Returns the nets rather than the finished curve because the anchor the curve hangs off — the
 * converted total across every account — is assembled a layer up, out of the account list and the
 * cached FX table. Same split as [GetAccountBalanceSeriesUseCase]: this owns the window and the
 * query, [com.georgeci.moneysurfer.domain.model.buildBalanceTrend] owns the fold, and the caller
 * pairs it with the total it is already printing so the curve cannot end anywhere else.
 *
 * The window's ends fall on month boundaries, which is what
 * [SpendAnalyticsRepository.netByMonth] requires to return whole months.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetMonthlyNetHistoryUseCase(
    private val spendAnalytics: SpendAnalyticsRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        months: Int = BalanceTrend.TREND_MONTHS,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<MonthlyNetHistory?> {
        // A zero-month window has no ends to query between; the fold is happy with no months, this
        // is not. Same guard as `buildAccountBalanceSeries` puts on its day count.
        require(months > 0) { "months must be positive, was $months" }
        return session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)
            val window = trailingMonths(
                anchor = clock.now().toLocalDateTime(timeZone).date.yearMonth,
                count = months,
            )

            baseCurrency(workspaceId).flatMapLatest { currency ->
                // The aggregate *filters* on the base currency, so a null one matches nothing.
                // Reporting "no history" beats querying with it and drawing a curve from no rows.
                currency ?: return@flatMapLatest flowOf(null)
                val scope = SpendScope(
                    workspaceId = workspaceId,
                    baseCurrency = currency,
                    window = TransactionPeriodWindow(
                        from = window.first().firstDay,
                        to = window.last().lastDay,
                    ),
                )
                spendAnalytics.netByMonth(scope).map { nets ->
                    MonthlyNetHistory(months = window, nets = nets, currency = currency)
                }
            }
        }
    }

    /**
     * The workspace base currency, live — the same reason [GetDailySpendSeriesUseCase] observes it:
     * the session pointer is restored independently of the `workspaces` row, so a device that has
     * not pulled the workspace yet resolves null, and a null would otherwise latch an empty curve
     * on screen for as long as the caller stayed subscribed.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()
}
