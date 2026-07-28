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
 * Canned spend rollups.
 *
 * [byCategory] answers from [slicesByWindow], keyed by the scope's window, because the callers
 * worth testing ask it twice with two different windows and the whole point of the assertion is
 * that the two answers are compared. A window with no entry returns nothing, which is what an
 * empty period looks like. The remaining queries return nothing: no insight rule reads them yet.
 */
class FakeSpendAnalyticsRepository(
    private val slicesByWindow: Map<TransactionPeriodWindow, List<CategorySpendSlice>> = emptyMap(),
) : SpendAnalyticsRepository {

    /** Every scope [byCategory] was asked for, in call order — the windows are worth asserting on. */
    val byCategoryScopes: MutableList<SpendScope> = mutableListOf()

    override fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>> {
        byCategoryScopes += scope
        return flowOf(slicesByWindow[scope.window].orEmpty())
    }

    override fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>> = flowOf(emptyList())

    override fun daily(scope: SpendScope): Flow<List<DailySpendPoint>> = flowOf(emptyList())

    override fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>> =
        flowOf(emptyList())

    override fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>> = flowOf(emptyList())
}
