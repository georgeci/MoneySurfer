package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.SpentByCategory
import com.georgeci.moneysurfer.domain.model.buildSpentByCategory
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * Where the chosen period's money went, category by category, for the active workspace — or null
 * while nothing backs it: nobody signed in, or a workspace whose base currency cannot be read.
 *
 * Spend comes from [SpendAnalyticsRepository.byCategory], one `GROUP BY` in SQLite, rather than
 * from folding the workspace's transactions in memory. That is the whole point of that interface,
 * and it is also why the per-category caps here are read straight off the budgets instead of
 * through `GetBudgetProgressUseCase`, which loads every transaction in the workspace per emission.
 *
 * Categories and budgets are read from the repositories with the workspace id this flow already
 * resolved, not through `GetCategoriesUseCase` / `GetBudgetsUseCase`. Those read
 * [SessionPointers.currentWorkspaceId] again, and two collectors of one pointer are not ordered
 * against each other — on a workspace switch that lets the new workspace's spend pair with the
 * previous one's categories, which is exactly the bug the safe-to-spend wiring had to unpick.
 *
 * [period] arrives as a flow and is required rather than defaulted, following
 * [GetSafeToSpendUseCase]: this is a spend figure, so it is only meaningful once the caller says
 * which span it answers for, and the dashboard's Week/Month switch has to reach it or the card
 * would keep showing the month while every widget beside it changed. Unlike safe-to-spend the
 * period here reshapes the query rather than re-picking a budget — the window *is* what
 * [SpendAnalyticsRepository.byCategory] groups over — so a change re-runs one `GROUP BY` rather
 * than the workspace's whole transaction list.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetCategorySpendUseCase(
    private val spendAnalytics: SpendAnalyticsRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        period: Flow<DashboardPeriod>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<SpentByCategory?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)

            combine(
                period.distinctUntilChanged(),
                baseCurrency(workspaceId),
            ) { chosen, currency -> chosen to currency }
                .flatMapLatest { (chosen, currency) ->
                    currency ?: return@flatMapLatest flowOf(null)
                    // Re-read on every emission rather than once per workspace: the window is the
                    // period's answer for *today*, and the period is the thing that changes.
                    val today = clock.now().toLocalDateTime(timeZone).date
                    breakdownOf(workspaceId, currency, chosen, today)
                }
        }

    /**
     * The workspace base currency, live.
     *
     * Observed rather than read once because the query *filters* on it: the session pointer lives
     * in preferences and is restored independently of the `workspaces` row, so a device that has
     * not pulled the workspace yet resolves null — which matches no transaction and would latch an
     * empty widget for as long as the caller stayed subscribed. Re-pointing after a currency change
     * falls out of the same subscription, and [distinctUntilChanged] keeps a rename from re-running
     * the aggregate.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()

    private fun breakdownOf(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
        period: DashboardPeriod,
        today: LocalDate,
    ): Flow<SpentByCategory> = combine(
        spendAnalytics.byCategory(SpendScope(workspaceId, currency, period.window(today))),
        categoryRepository.getByWorkspaceId(workspaceId),
        budgetRepository.getByWorkspaceId(workspaceId),
    ) { slices, categories, budgets ->
        buildSpentByCategory(
            slices = slices,
            categories = categories,
            budgets = budgets,
            currency = currency,
            window = period.window(today),
            // Only a cap on the selected cadence may speak: a monthly limit against a week of
            // spend would read as headroom until the month is already blown.
            capPeriod = period.budgetPeriod,
        )
    }
}
