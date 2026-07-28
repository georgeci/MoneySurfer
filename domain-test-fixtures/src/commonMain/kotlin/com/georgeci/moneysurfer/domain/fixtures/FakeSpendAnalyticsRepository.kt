package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory [SpendAnalyticsRepository] seeded with daily points.
 *
 * [daily] applies the scope's window the way the SQL does, so a caller that asks for the wrong
 * window sees the wrong days here too rather than being handed its seed back whole. [lastScope]
 * records what was asked for, for the tests that assert on the query rather than on the answer.
 *
 * The other four aggregates answer empty: nothing seeds them yet, and the day this fake backs a
 * second widget is the day to give them the same treatment [daily] gets.
 */
class FakeSpendAnalyticsRepository(
    daily: List<DailySpendPoint> = emptyList(),
) : SpendAnalyticsRepository {

    private val dailyPoints = MutableStateFlow(daily)

    var lastScope: SpendScope? = null
        private set

    fun setDaily(points: List<DailySpendPoint>) {
        dailyPoints.value = points
    }

    override fun daily(scope: SpendScope): Flow<List<DailySpendPoint>> {
        lastScope = scope
        return dailyPoints.map { points ->
            points.filter { it.date in scope.window }.sortedBy { it.date }
        }
    }

    override fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>> = flowOf(emptyList())

    override fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>> = flowOf(emptyList())

    override fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>> = flowOf(emptyList())

    override fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>> = flowOf(emptyList())
}
