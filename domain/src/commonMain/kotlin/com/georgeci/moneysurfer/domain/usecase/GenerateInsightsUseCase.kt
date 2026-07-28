package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.insight.Insight
import com.georgeci.moneysurfer.domain.insight.InsightInput
import com.georgeci.moneysurfer.domain.insight.generateInsights
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.RecurringRuleRepository
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.core.annotation.Single

/**
 * The two or three sentences the dashboard's Insights widget shows: which category moved, whether
 * the month is running hot, and what the live subscriptions come to.
 *
 * Reads only aggregates — two `GROUP BY` rollups, the category tree and the recurring rules — so
 * the amount of data crossing the boundary stays in the tens of rows whatever the workspace holds.
 * The rules themselves are a pure function; everything here is the plumbing that feeds them.
 *
 * Re-emits when any input moves: logging a transaction changes the numbers, renaming a category
 * changes the sentence, pausing a subscription changes the count, and the workspace base currency
 * decides which rows are counted at all.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GenerateInsightsUseCase(
    private val spendAnalytics: SpendAnalyticsRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<List<Insight>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(emptyList())

            val today = clock.now().toLocalDateTime(timeZone).date
            baseCurrency(workspaceId).flatMapLatest { currency ->
                // The aggregates *filter* on the base currency, so a null one matches nothing.
                // Emitting empty rather than querying keeps a device that has not pulled the
                // workspace row yet from showing insights built from no rows at all.
                currency ?: return@flatMapLatest flowOf(emptyList())
                insights(workspaceId, currency, today)
            }
        }

    /**
     * The workspace base currency, live.
     *
     * Observed rather than read once, for the reason
     * [GetCategorySpendHistoryUseCase] documents: the session pointer is restored independently of
     * the `workspaces` row, so a latched `null` would leave an empty widget on screen for as long
     * as the caller stayed subscribed.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()

    private fun insights(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
        today: LocalDate,
    ): Flow<List<Insight>> {
        val (current, previous) = monthToDateWindows(today)
        return combine(
            spendAnalytics.byCategory(SpendScope(workspaceId, currency, current)),
            spendAnalytics.byCategory(SpendScope(workspaceId, currency, previous)),
            categoryRepository.getByWorkspaceId(workspaceId),
            recurringRuleRepository.getByWorkspaceId(workspaceId),
        ) { currentSpend, previousSpend, categories, rules ->
            val expenseCategoryIds = categories
                .filter { it.type == CategoryType.EXPENSE }
                .mapTo(mutableSetOf()) { it.id }
            generateInsights(
                InsightInput(
                    periodKey = today.yearMonth.toString(),
                    currency = currency,
                    currentSpend = currentSpend,
                    previousSpend = previousSpend,
                    categoryNames = categories.associate { it.id to it.name },
                    // A salary paid in every month is a schedule, not a subscription.
                    expenseSchedules = rules.filter { it.categoryId in expenseCategoryIds },
                ),
            )
        }
    }
}

/**
 * The two windows every comparison rule reads: this month so far, and the same stretch of the
 * month before it.
 *
 * Month-to-date against month-to-date, *not* against the whole of last month. Three days of July
 * measured against all of June reads as a 90% fall in every category, so an engine built that way
 * would spend the first week of every month congratulating the user for not having spent the money
 * yet. Both windows are the same number of days, so the comparison means something on the 2nd as
 * well as on the 30th.
 *
 * The previous window is clamped to its own month's last day, so the 31st compares against the
 * whole of a 28-day February rather than spilling into March.
 *
 * Built directly rather than through `periodWindow(Month, ...)`, which always spans a whole
 * calendar month — the point here is a part-month window with a matching part-month baseline.
 */
private fun monthToDateWindows(
    today: LocalDate,
): Pair<TransactionPeriodWindow, TransactionPeriodWindow> {
    val currentStart = LocalDate(today.year, today.month, 1)
    val previousStart = currentStart.minus(1, DateTimeUnit.MONTH)
    val previousEnd = minOf(
        previousStart.plus(today.day - 1, DateTimeUnit.DAY),
        currentStart.minus(1, DateTimeUnit.DAY),
    )
    return TransactionPeriodWindow(currentStart, today) to
        TransactionPeriodWindow(previousStart, previousEnd)
}
