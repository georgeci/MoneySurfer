package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.entity.CategorySpendSliceEntity
import com.georgeci.moneysurfer.data.db.entity.CurrencySpendEntity
import com.georgeci.moneysurfer.data.db.entity.DailySpendEntity
import com.georgeci.moneysurfer.data.db.entity.MerchantSpendEntity
import com.georgeci.moneysurfer.data.db.entity.MonthlyTypeTotalEntity
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import org.koin.core.annotation.Single

/**
 * [SpendAnalyticsRepository] over the aggregate queries in [TransactionDao].
 *
 * Injects the DAO directly, as `CategorySpendRepositoryImpl` does — there is no transaction-shaped
 * intermediate to reuse here, and routing a `GROUP BY` through a repository that returns whole
 * rows is what this class exists to avoid.
 *
 * Every flow is [conflate]d: Room's invalidation tracker fires per write, and
 * `TransactionSyncPlugin.applyDoc` writes a pulled batch row by row, so an un-conflated aggregate
 * would recompute once per row for a screen that can only draw the last result anyway.
 */
@Single(binds = [SpendAnalyticsRepository::class])
class SpendAnalyticsRepositoryImpl(
    private val dao: TransactionDao,
) : SpendAnalyticsRepository {

    override fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>> =
        dao.getSpendByCategory(
            workspaceId = scope.workspaceId.value,
            baseCurrency = scope.baseCurrency.value,
            fromDate = scope.fromDate(),
            toDateExclusive = scope.toDateExclusive(),
        ).map { rows -> rows.map(CategorySpendSliceEntity::toDomain) }.conflate()

    override fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>> =
        dao.getNetTotalsByMonth(
            workspaceId = scope.workspaceId.value,
            baseCurrency = scope.baseCurrency.value,
            fromDate = scope.fromDate(),
            toDateExclusive = scope.toDateExclusive(),
        ).map { rows -> rows.toMonthlyNets() }.conflate()

    override fun daily(scope: SpendScope): Flow<List<DailySpendPoint>> =
        dao.getDailySpend(
            workspaceId = scope.workspaceId.value,
            baseCurrency = scope.baseCurrency.value,
            fromDate = scope.fromDate(),
            toDateExclusive = scope.toDateExclusive(),
        ).map { rows -> rows.mapNotNull(DailySpendEntity::toDomain) }.conflate()

    override fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>> =
        dao.getTopMerchants(
            workspaceId = scope.workspaceId.value,
            baseCurrency = scope.baseCurrency.value,
            fromDate = scope.fromDate(),
            toDateExclusive = scope.toDateExclusive(),
            limit = limit,
        ).map { rows -> rows.map(MerchantSpendEntity::toDomain) }.conflate()

    override fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>> =
        dao.getSpendByExcludedCurrency(
            workspaceId = scope.workspaceId.value,
            baseCurrency = scope.baseCurrency.value,
            fromDate = scope.fromDate(),
            toDateExclusive = scope.toDateExclusive(),
        ).map { rows -> rows.map(CurrencySpendEntity::toDomain) }.conflate()
}

private fun SpendScope.fromDate(): String? = window.from?.toString()

/**
 * The window is closed at the top ([com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow]
 * models what the transactions list needs), the predicate is half-open. Converting here rather
 * than comparing with `<=` keeps the SQL identical to the one budgets and the aligned category
 * trend use, so the three can only agree.
 */
private fun SpendScope.toDateExclusive(): String? =
    window.to?.plus(1, DateTimeUnit.DAY)?.toString()

private fun CategorySpendSliceEntity.toDomain(): CategorySpendSlice = CategorySpendSlice(
    categoryId = categoryId?.let(::CategoryId),
    total = Money.fromMinor(totalMinor),
    transactionCount = transactionCount,
)

private fun MerchantSpendEntity.toDomain(): MerchantSpend = MerchantSpend(
    merchant = merchant,
    total = Money.fromMinor(totalMinor),
    transactionCount = transactionCount,
)

private fun CurrencySpendEntity.toDomain(): CurrencyTotal = CurrencyTotal(
    currencyCode = CurrencyCode(currencyCode),
    amount = Money.fromMinor(totalMinor),
)

/**
 * Null for a row whose date will not parse — dropping one unreadable day beats failing the whole
 * screen, the same defence [CategorySpendRepositoryImpl] applies to its months.
 *
 * Kept as a second line rather than a live path: the query's `operationDate = date(operationDate)`
 * term already admits nothing but a canonical `YYYY-MM-DD`, which [LocalDate.parse] always accepts,
 * so nothing reaching here can fail. That is why coverage reports the null branch as never taken —
 * it is unreachable by construction, and only stays because `operationDate` is a plain text column
 * one predicate change away from letting something else through.
 */
private fun DailySpendEntity.toDomain(): DailySpendPoint? {
    val parsed = runCatching { LocalDate.parse(operationDate) }.getOrNull() ?: return null
    return DailySpendPoint(date = parsed, total = Money.fromMinor(totalMinor))
}

/**
 * Folds the `(month, type)` cells into one row per month, oldest first.
 *
 * A month arrives as one row per type it booked, so the side it did not book folds to zero rather
 * than dropping the column from the chart. Months keep the order SQLite returned them in, which
 * `ORDER BY month ASC` already makes ascending.
 *
 * The unparseable-month branch is the same unreachable-by-construction guard [DailySpendEntity]
 * carries: `substr` over a canonical date cannot produce anything [YearMonth.parse] refuses.
 */
private fun List<MonthlyTypeTotalEntity>.toMonthlyNets(): List<MonthlyNet> =
    groupBy { it.month }.mapNotNull { (month, rows) ->
        val parsed = runCatching { YearMonth.parse(month) }.getOrNull() ?: return@mapNotNull null
        MonthlyNet(
            month = parsed,
            income = rows.totalOf(TransactionType.INCOME),
            expense = rows.totalOf(TransactionType.EXPENSE),
        )
    }

private fun List<MonthlyTypeTotalEntity>.totalOf(type: TransactionType): Money =
    Money.fromMinor(filter { it.type == type.name }.sumOf { it.totalMinor })
