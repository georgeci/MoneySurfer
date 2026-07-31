package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.InsightsSelection
import com.georgeci.moneysurfer.domain.model.SpendInsights
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.SpentByCategory
import com.georgeci.moneysurfer.domain.model.buildNetTrend
import com.georgeci.moneysurfer.domain.model.buildSpentByCategory
import com.georgeci.moneysurfer.domain.model.trailingMonths
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.koin.core.annotation.Single

/**
 * Everything the insights screen shows for the period it is pointed at — or null while nothing backs
 * it: nobody signed in, or a workspace whose base currency cannot be read.
 *
 * One use case for all four rollups rather than one per card, because they have to agree. Each of
 * them filters on the workspace and its base currency, and two collectors of
 * [SessionPointers.currentWorkspaceId] are not ordered against each other — on a workspace switch
 * that lets one card's spend pair with another card's categories, the bug the safe-to-spend wiring
 * had to unpick. Resolving the workspace and the currency once and fanning out from there makes a
 * mismatched screen unrepresentable.
 *
 * Spend comes from [SpendAnalyticsRepository], four `GROUP BY` rollups, rather than from folding the
 * workspace's transactions in memory — the same reason [GetCategorySpendUseCase] gives, and the
 * whole point of that interface.
 *
 * [selection] arrives as a flow and is required rather than defaulted: these are spend figures, so
 * they are only meaningful once the caller says which span they answer for, and the screen's pager
 * has to reach them or paging would change the label and nothing else.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetSpendInsightsUseCase(
    private val spendAnalytics: SpendAnalyticsRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
) {

    operator fun invoke(selection: Flow<InsightsSelection>): Flow<SpendInsights?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)

            combine(
                selection.distinctUntilChanged(),
                baseCurrency(workspaceId),
            ) { chosen, currency -> chosen to currency }
                .flatMapLatest { (chosen, currency) ->
                    // The aggregates *filter* on the base currency, so a null one matches nothing.
                    // Emitting null rather than querying keeps a device that has not pulled the
                    // workspace row yet from drawing a screen built from no rows at all.
                    currency ?: return@flatMapLatest flowOf(null)
                    insightsOf(workspaceId, currency, chosen)
                }
        }

    /**
     * The workspace base currency, live.
     *
     * Observed rather than read once because the queries *filter* on it: the session pointer lives
     * in preferences and is restored independently of the `workspaces` row, so a device that has not
     * pulled the workspace yet resolves null — which matches no transaction and would latch an empty
     * screen for as long as the caller stayed subscribed. Re-pointing after a currency change falls
     * out of the same subscription, and [distinctUntilChanged] keeps a rename from re-running four
     * aggregates.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()

    private fun insightsOf(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
        selection: InsightsSelection,
    ): Flow<SpendInsights> {
        val scope = SpendScope(workspaceId, currency, selection.window)
        val months = trailingMonths(selection.anchor.yearMonth, SpendInsights.MONTH_COLUMNS)
        val monthsScope = SpendScope(workspaceId, currency, monthsWindow(months))
        return combine(
            breakdown(scope, workspaceId, selection),
            spendAnalytics.netByMonth(monthsScope),
            spendAnalytics.topMerchants(scope, SpendInsights.TOP_MERCHANTS),
            spendAnalytics.excludedByCurrency(scope),
        ) { breakdown, monthRows, merchants, excluded ->
            SpendInsights(
                selection = selection,
                currency = currency,
                breakdown = breakdown,
                months = buildNetTrend(months, monthRows),
                merchants = merchants,
                excludedByCurrency = excluded,
            )
        }
    }

    /**
     * The period's spend joined to the categories that name it, through the same builder the
     * dashboard's donut and spent-by-category widgets go through — so a category's figure on this
     * screen can never disagree with the same category's figure on the dashboard.
     *
     * The budgets arm is what that builder needs to resolve per-category caps. This screen draws no
     * cap overlay today; carrying them costs one read of tens of rows and is the difference between
     * reusing the shared join and forking a second, cap-less one.
     */
    private fun breakdown(
        scope: SpendScope,
        workspaceId: WorkspaceId,
        selection: InsightsSelection,
    ): Flow<SpentByCategory> = combine(
        spendAnalytics.byCategory(scope),
        categoryRepository.getByWorkspaceId(workspaceId),
        budgetRepository.getByWorkspaceId(workspaceId),
    ) { slices, categories, budgets ->
        buildSpentByCategory(
            slices = slices,
            categories = categories,
            budgets = budgets,
            currency = scope.baseCurrency,
            window = scope.window,
            // Only a cap on the selected cadence may speak — see `buildSpentByCategory`.
            capPeriod = selection.mode.budgetPeriod,
        )
    }
}

/**
 * The span [months] covers, whole months at both ends.
 *
 * Built through `periodWindow(Month, ...)` rather than by hand because `netByMonth` groups *inside*
 * the window it is given: an end that is not a month boundary comes back as a part-month row under a
 * full month's name, which `MonthlyNet.month` cannot express.
 */
private fun monthsWindow(months: List<YearMonth>): TransactionPeriodWindow = TransactionPeriodWindow(
    from = wholeMonth(months.first()).from,
    to = wholeMonth(months.last()).to,
)

private fun wholeMonth(month: YearMonth): TransactionPeriodWindow =
    periodWindow(TransactionPeriodMode.Month, LocalDate(month.year, month.month, 1))
