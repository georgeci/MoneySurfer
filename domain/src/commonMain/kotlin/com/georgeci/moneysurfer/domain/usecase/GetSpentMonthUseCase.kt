package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.SpentMonth
import com.georgeci.moneysurfer.domain.model.expenseTotal
import com.georgeci.moneysurfer.domain.model.monthlySpendCap
import com.georgeci.moneysurfer.domain.model.previousSpentMonthWindow
import com.georgeci.moneysurfer.domain.model.spentMonthWindow
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * What the active workspace's month has cost so far, or null while nothing backs the figure —
 * nobody signed in, no workspace selected, or a workspace row the device has not pulled yet.
 *
 * A missing budget is *not* a missing month: [SpentMonth.cap] simply resolves null and the widget
 * says so. Null here means the app cannot name the amount or its currency at all, which is the one
 * thing it must not print a number for.
 *
 * Two aggregate reads rather than one, both `netByMonth`: the month-to-date figure and the same
 * stretch of the month before it. One window spanning both months would return a whole previous
 * month beside a partial current one, which is the comparison
 * [com.georgeci.moneysurfer.domain.model.SpentMonth.previousSpent] exists to avoid. Each window
 * covers a single month, so the part-month caveat on
 * [SpendAnalyticsRepository.netByMonth] is exactly what is wanted here rather than a hazard.
 *
 * The cap is looked up against *this* flow's workspace rather than against whatever
 * [GetBudgetsUseCase] happens to be answering for — the two subscribe to
 * `SessionPointers.currentWorkspaceId` separately, so mid-switch they can disagree about which
 * workspace is current. [monthlySpendCap] refuses the mismatched pairing instead of measuring one
 * workspace's spend against another's limit; see [GetBurnRateUseCase], which pairs them the same way.
 *
 * "Today" is read from the clock once per workspace-and-currency emission, not per row, so a
 * session left open across midnight keeps yesterday's windows until something re-subscribes. That
 * matches every other date-scoped query here, and a dashboard is re-collected on every return to
 * the screen.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetSpentMonthUseCase(
    private val spendAnalytics: SpendAnalyticsRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val getBudgets: GetBudgetsUseCase,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<SpentMonth?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)
            val today = clock.now().toLocalDateTime(timeZone).date

            baseCurrency(workspaceId).flatMapLatest { currency ->
                currency ?: return@flatMapLatest flowOf(null)
                combine(
                    expenseIn(workspaceId, currency, spentMonthWindow(today)),
                    expenseIn(workspaceId, currency, previousSpentMonthWindow(today)),
                    getBudgets(),
                ) { spent, previousSpent, budgets ->
                    SpentMonth(
                        today = today,
                        spent = spent,
                        previousSpent = previousSpent,
                        cap = budgets.monthlySpendCap(workspaceId),
                        currency = currency,
                    )
                }
            }
        }.distinctUntilChanged()

    /**
     * Spend inside one window as a single magnitude.
     *
     * [distinctUntilChanged] here is about the *rows*, not the budgets: Room's invalidation tracker
     * fires per write, so a sync pull re-runs the aggregate once per batch, and most of those
     * batches leave this window's total exactly where it was. A budget edit that does not move the
     * cap is absorbed by the one on the result instead.
     */
    private fun expenseIn(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
        window: TransactionPeriodWindow,
    ) = spendAnalytics.netByMonth(
        SpendScope(workspaceId = workspaceId, baseCurrency = currency, window = window),
    ).map { months -> months.expenseTotal() }.distinctUntilChanged()

    /**
     * The workspace base currency, live — the same reason [GetDailySpendSeriesUseCase] observes it
     * rather than reading it once: the query *filters* on it, so a device that has not pulled the
     * workspace row yet resolves null, and a null would otherwise latch a zero on screen for as
     * long as the caller stayed subscribed.
     *
     * Null is reported as "no figure" rather than queried with, because a spend amount the app
     * cannot name a currency for is not one the widget can print.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()
}
