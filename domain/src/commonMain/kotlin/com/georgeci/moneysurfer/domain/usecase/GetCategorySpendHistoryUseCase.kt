package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.CategorySpendHistory
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.model.buildCategorySpendHistory
import com.georgeci.moneysurfer.domain.model.trailingMonths
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.CategorySpendRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.core.annotation.Single

/**
 * The trailing-[CategorySpendHistory.TREND_MONTHS] spend picture for one category: the trend
 * columns, this month's roll-up, and the per-subcategory split.
 *
 * Re-emits when any of the three inputs changes — renaming or reparenting a subcategory reshapes
 * the split without a transaction moving, logging a transaction changes the numbers without the
 * tree moving, and the workspace base currency decides which rows count at all, so all three
 * flows have to be live.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetCategorySpendHistoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val spendRepository: CategorySpendRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        categoryId: CategoryId,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<CategorySpendHistory> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(CategorySpendHistory.Empty)

            val months = trailingMonths(
                anchor = clock.now().toLocalDateTime(timeZone).date.yearMonth,
                count = CategorySpendHistory.TREND_MONTHS,
            )

            baseCurrency(workspaceId).flatMapLatest { currency ->
                historyOf(workspaceId, categoryId, currency, months)
            }
        }

    /**
     * The workspace base currency, live.
     *
     * Observed rather than read once, because the query *filters* on it: the session pointer lives
     * in preferences and is restored independently of the `workspaces` row, so a device that has
     * not pulled the workspace yet resolves `null` — which matches no transaction and would leave
     * an empty trend latched on screen for as long as the caller stayed subscribed. Re-pointing on
     * a currency change falls out of the same subscription.
     *
     * [distinctUntilChanged] keeps a rename or an archive flip from re-running the aggregate.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()

    private fun historyOf(
        workspaceId: WorkspaceId,
        categoryId: CategoryId,
        baseCurrency: CurrencyCode?,
        months: List<YearMonth>,
    ): Flow<CategorySpendHistory> =
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
                baseCurrency = baseCurrency,
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

/**
 * A transfer category books no income and no expense of its own — the pair of legs is written
 * against the accounts. Reading it as an expense keeps the query well-formed and the trend
 * honestly empty rather than special-casing a third branch through every caller.
 */
private fun CategoryType.toTransactionType(): TransactionType = when (this) {
    CategoryType.INCOME -> TransactionType.INCOME
    CategoryType.EXPENSE, CategoryType.TRANSFER -> TransactionType.EXPENSE
}
