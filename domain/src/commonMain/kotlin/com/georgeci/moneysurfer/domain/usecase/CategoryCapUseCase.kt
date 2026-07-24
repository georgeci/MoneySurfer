package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryCapCoverage
import com.georgeci.moneysurfer.domain.model.resolveCategoryCapCoverage
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * Both directions of the category form's monthly-cap shortcut: what the control may show, and what
 * saving it does to the budget behind it.
 *
 * There is no `cap` field on `Category` to write to — the cap *is* a budget naming exactly one
 * category (md/categories.md, decision 2). Read and write share [resolveCategoryCapCoverage] so
 * the screen and the write path can never disagree about which budget the control speaks for.
 */
@Single
class CategoryCapUseCase(
    private val budgetRepository: BudgetRepository,
    private val getBudgets: GetBudgetsUseCase,
    private val clock: ClockUseCase,
) {

    /**
     * Which budget the control speaks for, tracked live. Re-resolving on every emission means a
     * budget created or archived elsewhere while the form is open is reflected here rather than
     * silently overwritten on save.
     */
    fun coverageOf(categoryId: CategoryId?): Flow<CategoryCapCoverage> =
        getBudgets().map { budgets -> resolveCategoryCapCoverage(categoryId, budgets) }

    /**
     * Applies the control to the backing budget: creates a single-category budget, updates its
     * amount, or deletes it when the field was cleared.
     *
     * Coverage is re-resolved here rather than trusted from the caller, so a screen holding a
     * stale state cannot write over a budget that appeared in the meantime.
     *
     * @param category the category as it was just saved — its id, name, type and workspace.
     * @param previousName the category's name before this save, or null when it was just created.
     *   Used to keep an untouched backing budget's name in step with a category rename.
     * @param cap the amount typed into the control, or null when it is empty (no cap).
     */
    suspend operator fun invoke(
        category: Category,
        previousName: String?,
        cap: Money?,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ) {
        val budgets = budgetRepository.getByWorkspaceId(category.workspaceId).first()
        val backing = when (val coverage = resolveCategoryCapCoverage(category.id, budgets)) {
            is CategoryCapCoverage.Editable -> coverage.budget
            // A shared limit already owns this category. The form shows the control read-only, and
            // the shortcut stays out of it rather than creating a second overlapping limit.
            is CategoryCapCoverage.Managed -> return
        }

        // Budgets cap spending, so an income category can never move one — it carries no cap, and
        // converting an expense category to income takes its backing budget with it.
        val target = cap?.takeIf { category.type == CategoryType.EXPENSE }
        val now = clock.now()

        when {
            target == null -> backing?.let { budgetRepository.delete(it.id) }

            backing == null -> budgetRepository.insert(
                Budget(
                    workspaceId = category.workspaceId,
                    name = category.name,
                    categoryIds = listOf(category.id),
                    amount = target,
                    period = BudgetPeriod.MONTHLY,
                    startDate = now.toLocalDateTime(timeZone).date,
                    createdAt = now,
                ),
            )

            else -> budgetRepository.update(
                backing.copy(
                    amount = target,
                    // Follow a category rename only while the budget still carries the old name.
                    // A name the user changed on the Budgets screen is theirs to keep.
                    name = if (backing.name == previousName) category.name else backing.name,
                ),
            )
        }
    }
}
