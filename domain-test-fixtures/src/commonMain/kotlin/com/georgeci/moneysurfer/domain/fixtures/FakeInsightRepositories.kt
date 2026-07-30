package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.RecurringRuleRepository
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Reactive in-memory stand-ins for what the insights engine reads, backed by [MutableStateFlow]
 * so a `combine` over them sees every write without manual re-emission.
 */
class FakeCategoryRepository(seed: List<Category> = emptyList()) : CategoryRepository {
    private val state = MutableStateFlow(seed)

    val rows: List<Category> get() = state.value

    override fun getAll(): Flow<List<Category>> = state

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> =
        state.map { rows -> rows.filter { it.workspaceId == workspaceId } }

    override suspend fun getById(id: CategoryId): Category? = state.value.firstOrNull { it.id == id }

    override suspend fun insert(category: Category) {
        state.value = state.value + category
    }

    override suspend fun update(category: Category) {
        state.value = state.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: CategoryId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeRecurringRuleRepository(seed: List<RecurringRule> = emptyList()) : RecurringRuleRepository {
    private val state = MutableStateFlow(seed)

    val rows: List<RecurringRule> get() = state.value

    override fun getAll(): Flow<List<RecurringRule>> = state

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<RecurringRule>> =
        state.map { rows -> rows.filter { it.workspaceId == workspaceId } }

    override suspend fun getById(id: RecurringRuleId): RecurringRule? =
        state.value.firstOrNull { it.id == id }

    override suspend fun insert(rule: RecurringRule) {
        state.value = state.value + rule
    }

    override suspend fun update(rule: RecurringRule) {
        state.value = state.value.map { if (it.id == rule.id) rule else it }
    }

    override suspend fun delete(id: RecurringRuleId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

/**
 * Canned spend rollups. The seeded queries are shaped by what their callers need to assert:
 *
 * - [byCategory] answers from [slicesByWindow], keyed by the scope's window, because the insight
 *   rules ask it twice with two different windows and the whole point of the assertion is that the
 *   two answers are compared. A window with no entry returns nothing, which is what an empty period
 *   looks like.
 * - [daily] answers from one seeded list but applies the scope's window the way the SQL does, so a
 *   caller that asks for the wrong week sees the wrong days here too rather than being handed its
 *   seed back whole. The burn rate derives both its chart and its month-to-date from a single
 *   window, so getting that window wrong has to be visible.
 * - [netByMonth] answers from [nets] the same way, keeping only the months the scope's window
 *   covers, so a balance curve folded over the wrong months is short here too.
 * - [topMerchants] and [excludedByCurrency] answer from one flat seed each and ignore the window:
 *   their callers assert on the recorded scope instead, which keeps a spec from having to spell out
 *   a window twice to say "these rows came back for it". [topMerchants] does honour the row limit,
 *   because that is a number its caller chooses.
 *
 * Every query records the scopes it was asked for, in call order.
 */
class FakeSpendAnalyticsRepository(
    private val slicesByWindow: Map<TransactionPeriodWindow, List<CategorySpendSlice>> = emptyMap(),
    daily: List<DailySpendPoint> = emptyList(),
    private val nets: List<MonthlyNet> = emptyList(),
    // Named apart from the queries they seed, as `nets` already is: a body reading
    // `flowOf(topMerchants)` inside `override fun topMerchants(...)` resolves to the seed, but
    // nobody should have to check.
    private val merchants: List<MerchantSpend> = emptyList(),
    private val excluded: List<CurrencyTotal> = emptyList(),
) : SpendAnalyticsRepository {

    private val dailyPoints = MutableStateFlow(daily)

    /** Every scope [byCategory] was asked for, in call order — the windows are worth asserting on. */
    val byCategoryScopes: MutableList<SpendScope> = mutableListOf()

    /** The same record for [daily]. */
    val dailyScopes: MutableList<SpendScope> = mutableListOf()

    /** The same record for [netByMonth] — the one query whose window is not the caller's period. */
    val netByMonthScopes: MutableList<SpendScope> = mutableListOf()

    /** The same record for [topMerchants], paired with the row limit each call asked for. */
    val topMerchantsScopes: MutableList<Pair<SpendScope, Int>> = mutableListOf()

    fun setDaily(points: List<DailySpendPoint>) {
        dailyPoints.value = points
    }

    override fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>> {
        byCategoryScopes += scope
        return flowOf(slicesByWindow[scope.window].orEmpty())
    }

    override fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>> {
        netByMonthScopes += scope
        // A month counts when its first day is inside the window — the same all-or-nothing call the
        // real query's month-boundary contract lets callers assume.
        return flowOf(nets.filter { it.month.firstDay in scope.window }.sortedBy { it.month })
    }

    override fun daily(scope: SpendScope): Flow<List<DailySpendPoint>> {
        dailyScopes += scope
        return dailyPoints.map { points ->
            points.filter { it.date in scope.window }.sortedBy { it.date }
        }
    }

    override fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>> {
        topMerchantsScopes += scope to limit
        return flowOf(merchants.take(limit))
    }

    override fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>> = flowOf(excluded)
}
