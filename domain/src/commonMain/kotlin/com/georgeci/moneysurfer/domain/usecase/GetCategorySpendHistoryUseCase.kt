package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.CategorySpendHistory
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.model.buildCategorySpendHistory
import com.georgeci.moneysurfer.domain.model.trailingMonths
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.CategorySpendRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.core.annotation.Single

/**
 * The trailing-[CategorySpendHistory.TREND_MONTHS] spend picture for one category: the trend
 * columns, this month's roll-up, and the per-subcategory split.
 *
 * Re-emits when either side changes — renaming or reparenting a subcategory reshapes the split
 * without a transaction moving, and logging a transaction changes the numbers without the tree
 * moving, so both flows have to be live.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetCategorySpendHistoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val spendRepository: CategorySpendRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        categoryId: CategoryId,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<CategorySpendHistory> =
        session.currentWorkspaceId.flow.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(CategorySpendHistory.Empty)

            val months = trailingMonths(
                anchor = clock.now().toLocalDateTime(timeZone).date.yearMonth,
                count = CategorySpendHistory.TREND_MONTHS,
            )

            categoryRepository.getByWorkspaceId(workspaceId).flatMapLatest { categories ->
                val root = categories.firstOrNull { it.id == categoryId }
                    ?: return@flatMapLatest flowOf(CategorySpendHistory.Empty)

                // Query the whole subtree in one go — the breakdown needs the per-child rows and
                // the trend needs them summed, so a parent-only query would just be re-fetched.
                val subtree = (CategoryTree.descendantsOf(categories, categoryId) + categoryId).toList()

                spendRepository.monthlyTotals(
                    workspaceId = workspaceId,
                    categoryIds = subtree,
                    type = root.type.toTransactionType(),
                    fromMonth = months.first(),
                    toMonth = months.last(),
                ).map { totals ->
                    buildCategorySpendHistory(
                        categories = categories,
                        rootId = categoryId,
                        totals = totals,
                        months = months,
                    )
                }
            }
        }
}

/**
 * A transfer category books no income and no expense of its own — the pair of legs is written
 * against the accounts. Reading it as an expense keeps the query well-formed and the trend
 * honestly empty rather than special-casing a third branch through every caller.
 */
private fun CategoryType.toTransactionType(): TransactionType = when (this) {
    CategoryType.INCOME -> TransactionType.INCOME
    CategoryType.EXPENSE, CategoryType.TRANSFER -> TransactionType.EXPENSE
}
