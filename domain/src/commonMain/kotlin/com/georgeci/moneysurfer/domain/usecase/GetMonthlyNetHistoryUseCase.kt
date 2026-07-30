package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.BalanceTrend
import com.georgeci.moneysurfer.domain.model.MonthlyNetHistory
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.next
import com.georgeci.moneysurfer.domain.model.trailingMonths
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

            combine(currentMonth(timeZone), baseCurrency(workspaceId), ::Pair)
                .flatMapLatest { (anchor, currency) ->
                    // The aggregate *filters* on the base currency, so a null one matches nothing.
                    // Reporting "no history" beats querying with it and drawing a curve from no rows.
                    currency ?: return@flatMapLatest flowOf(null)
                    history(workspaceId, currency, trailingMonths(anchor, months))
                }
        }
    }

    private fun history(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
        window: List<YearMonth>,
    ): Flow<MonthlyNetHistory> {
        val scope = SpendScope(
            workspaceId = workspaceId,
            baseCurrency = currency,
            window = TransactionPeriodWindow(
                from = window.first().firstDay,
                to = window.last().lastDay,
            ),
        )
        return spendAnalytics.netByMonth(scope).map { nets ->
            MonthlyNetHistory(months = window, nets = nets, currency = currency)
        }
    }

    /**
     * The month the trend is anchored on, re-emitted when the calendar rolls into the next one.
     *
     * Read once, the window would freeze for the life of the subscription: the workspace pointer
     * does not re-emit at midnight, so a session left open across a month boundary would keep
     * printing the finished month's net under copy that says "this month", with the new month's
     * transactions outside every queried month and no way to self-correct short of re-subscribing.
     * [GenerateInsightsUseCase] sleeps to the next local midnight for the same reason; this one only
     * has to wake on the first of a month, so it sleeps to the start of the next one.
     */
    private fun currentMonth(timeZone: TimeZone): Flow<YearMonth> = flow {
        while (true) {
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            emit(today.yearMonth)
            val nextMonth = today.yearMonth.next().firstDay.atStartOfDayIn(timeZone)
            // Floored, like the insights engine's rollover: a clock that jumps backwards must cost a
            // minute of staleness rather than re-running the aggregate as fast as the dispatcher can.
            delay((nextMonth - now).coerceAtLeast(MIN_ROLLOVER_SLEEP))
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

/**
 * Floor on the rollover sleep, matching the insights engine's. A clock that jumps backwards costs a
 * minute of staleness instead of a busy loop.
 */
private val MIN_ROLLOVER_SLEEP: Duration = 1.minutes
