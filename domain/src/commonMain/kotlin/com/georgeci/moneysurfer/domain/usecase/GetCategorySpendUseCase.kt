package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.SpentByCategory
import com.georgeci.moneysurfer.domain.model.buildSpentByCategory
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
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
 * Where this month's money went, category by category, for the active workspace — or null while
 * nothing backs it: nobody signed in, or a workspace whose base currency cannot be read.
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
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<SpentByCategory?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)

            val window = periodWindow(
                mode = TransactionPeriodMode.Month,
                anchor = clock.now().toLocalDateTime(timeZone).date,
            )

            baseCurrency(workspaceId).flatMapLatest { currency ->
                currency ?: return@flatMapLatest flowOf(null)
                breakdownOf(workspaceId, currency, window)
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
        window: TransactionPeriodWindow,
    ): Flow<SpentByCategory> = combine(
        spendAnalytics.byCategory(SpendScope(workspaceId, currency, window)),
        categoryRepository.getByWorkspaceId(workspaceId),
        budgetRepository.getByWorkspaceId(workspaceId),
    ) { slices, categories, budgets ->
        buildSpentByCategory(
            slices = slices,
            categories = categories,
            budgets = budgets,
            currency = currency,
            window = window,
        )
    }
}
