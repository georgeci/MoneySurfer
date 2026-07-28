package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.DailySpendSeries
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.dailySpendSeries
import com.georgeci.moneysurfer.domain.model.dailySpendSeriesWindow
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * The active workspace's last week of daily spend, or null while nothing backs it — nobody signed
 * in, no workspace selected, or a workspace row the device has not pulled yet.
 *
 * One `GROUP BY` answers both halves of what the burn rate needs: the days the chart draws and the
 * month-to-date its projection starts from. See
 * [com.georgeci.moneysurfer.domain.model.dailySpendSeriesWindow] for why that is one window.
 *
 * "Today" is read from the clock once per workspace-and-currency emission, not per row, so a session
 * left open across midnight keeps yesterday's window until something re-subscribes. That matches
 * every other date-scoped query here ([GetCategorySpendHistoryUseCase] resolves its months the same
 * way), and a dashboard is re-collected on every return to the screen.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetDailySpendSeriesUseCase(
    private val spendAnalytics: SpendAnalyticsRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<DailySpendSeries?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)
            val today = clock.now().toLocalDateTime(timeZone).date

            baseCurrency(workspaceId).flatMapLatest { currency ->
                currency ?: return@flatMapLatest flowOf(null)
                val scope = SpendScope(
                    workspaceId = workspaceId,
                    baseCurrency = currency,
                    window = dailySpendSeriesWindow(today),
                )
                spendAnalytics.daily(scope).map { points -> dailySpendSeries(points, today, currency) }
            }
        }

    /**
     * The workspace base currency, live — the same reason [GetCategorySpendHistoryUseCase] observes
     * it rather than reading it once: the query *filters* on it, so a device that has not pulled the
     * workspace row yet resolves null, and a null would otherwise latch an empty chart on screen for
     * as long as the caller stayed subscribed.
     *
     * Null is reported as "no series" rather than queried with, because a spend figure the app
     * cannot name a currency for is not a figure the widget can print.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()
}
