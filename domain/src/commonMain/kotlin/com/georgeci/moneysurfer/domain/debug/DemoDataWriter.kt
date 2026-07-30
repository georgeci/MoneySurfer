package com.georgeci.moneysurfer.domain.debug

import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.time.Instant

/**
 * What the workspace looked like before the run, plus the categories this run had to create to
 * make it plannable at all — the one count the generator cannot report on itself.
 */
internal data class DemoDataStart(
    val snapshot: DemoDataSnapshot,
    val categoriesSeeded: Int,
)

/**
 * The IO half of [DebugDataPrefiller]: reads what the workspace already holds, then writes the
 * plan the generator produced.
 *
 * Every write goes through the ordinary repositories rather than a bulk path of its own — that is
 * what puts the rows on the outbox, and it is the only reason prefilled data reaches Firestore for
 * a signed-in tester. Transactions specifically go through [ApplyTransactionChangeUseCase], the
 * single writer that keeps `Account.balance` a projection of the ledger; inserting them straight
 * would leave every account showing its opening balance forever.
 */
@Single
internal class DemoDataWriter(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val goalContributionRepository: GoalContributionRepository,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    /**
     * State the generator plans against. Categories are seeded first when the workspace has none:
     * a transaction with no category is legal but tells the tester nothing, and every screen that
     * groups by category would come up empty.
     */
    suspend fun snapshot(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
        now: Instant,
    ): DemoDataStart {
        val seeded = seedCategoriesIfMissing(workspaceId, now)
        return DemoDataStart(
            snapshot = DemoDataSnapshot(
                workspaceId = workspaceId,
                currency = currency,
                accounts = accountRepository.getByWorkspaceId(workspaceId).first(),
                categories = categoryRepository.getByWorkspaceId(workspaceId).first(),
                budgetNames = budgetRepository.getByWorkspaceId(workspaceId).first().map { it.name }.toSet(),
                goalTitles = savingsGoalRepository.getByWorkspaceId(workspaceId).first().map { it.title }.toSet(),
            ),
            categoriesSeeded = seeded,
        )
    }

    suspend fun write(plan: DemoDataPlan, categoriesSeeded: Int): DebugPrefillReport {
        plan.accounts.forEach { accountRepository.insert(it) }
        plan.transactions.forEach { applyTransactionChange(old = null, new = it) }
        plan.budgets.forEach { budgetRepository.insert(it) }
        plan.goals.forEach { savingsGoalRepository.insert(it) }
        plan.contributions.forEach { goalContributionRepository.insert(it) }
        return DebugPrefillReport(
            accounts = plan.accounts.size,
            categories = categoriesSeeded,
            transactions = plan.transactions.size,
            budgets = plan.budgets.size,
            goals = plan.goals.size,
        )
    }

    /**
     * Normally a no-op: `CreateWorkspaceUseCase` seeds the same list when the workspace is created.
     * It matters for a workspace pulled from a device that deleted its categories, and for the
     * hand-built ones in tests.
     */
    private suspend fun seedCategoriesIfMissing(workspaceId: WorkspaceId, now: Instant): Int {
        if (categoryRepository.getByWorkspaceId(workspaceId).first().isNotEmpty()) return 0
        DEFAULT_CATEGORY_SEEDS.forEach { seed ->
            categoryRepository.insert(
                Category(
                    id = CategoryId.uuid(),
                    workspaceId = workspaceId,
                    name = seed.name,
                    type = seed.type,
                    parentId = null,
                    createdAt = now,
                    systemKind = seed.systemKind,
                ),
            )
        }
        return DEFAULT_CATEGORY_SEEDS.size
    }
}
