package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.entity.CategoryMonthlyTotalEntity
import com.georgeci.moneysurfer.domain.model.CategoryMonthlyTotal
import com.georgeci.moneysurfer.domain.model.next
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategorySpendRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.YearMonth
import org.koin.core.annotation.Single

@Single(binds = [CategorySpendRepository::class])
class CategorySpendRepositoryImpl(
    private val dao: TransactionDao,
) : CategorySpendRepository {

    override fun monthlyTotals(
        workspaceId: WorkspaceId,
        categoryIds: List<CategoryId>,
        type: TransactionType,
        baseCurrency: CurrencyCode?,
        fromMonth: YearMonth,
        toMonth: YearMonth,
    ): Flow<List<CategoryMonthlyTotal>> {
        // `IN ()` is a syntax error in SQLite, and an empty subtree is a normal state — a
        // category can be deleted out from under an open detail screen.
        if (categoryIds.isEmpty()) return flowOf(emptyList())

        return dao.getMonthlyTotalsByCategory(
            workspaceId = workspaceId.value,
            categoryIds = categoryIds.map { it.value },
            type = type.name,
            baseCurrency = baseCurrency?.value,
            fromDate = fromMonth.firstDay.toString(),
            toDateExclusive = toMonth.next().firstDay.toString(),
        ).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    /**
     * Null for a row whose `month` will not parse. `substr` cannot produce one from a valid
     * `operationDate`, but the column is a plain string an older or foreign writer could have
     * put anything in, and dropping one unreadable month beats failing the whole screen.
     */
    private fun CategoryMonthlyTotalEntity.toDomain(): CategoryMonthlyTotal? {
        val parsed = runCatching { YearMonth.parse(month) }.getOrNull() ?: return null
        return CategoryMonthlyTotal(
            categoryId = CategoryId(categoryId),
            month = parsed,
            total = Money.fromMinor(totalMinor),
            transactionCount = transactionCount,
        )
    }
}
