package com.georgeci.moneysurfer.feature.budget

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Replay-1 in-memory repositories: every write re-emits, so a view model sees its own effects. */
internal class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    private val flow = MutableStateFlow(initial)
    val deleted = mutableListOf<BudgetId>()

    fun snapshot(): List<Budget> = flow.value

    fun emit(budgets: List<Budget>) {
        flow.value = budgets
    }

    override fun getAll(): Flow<List<Budget>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> = flow
    override suspend fun getById(id: BudgetId): Budget? = flow.value.firstOrNull { it.id == id }
    override suspend fun insert(budget: Budget) {
        flow.value = flow.value + budget
    }

    override suspend fun update(budget: Budget) {
        flow.value = flow.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun setActive(id: BudgetId, isActive: Boolean) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isActive = isActive) else it }
    }

    override suspend fun delete(id: BudgetId) {
        deleted += id
        flow.value = flow.value.filterNot { it.id == id }
    }
}

internal class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    private val flow = MutableStateFlow(initial)

    fun emit(transactions: List<Transaction>) {
        flow.value = transactions
    }

    override fun getAll(): Flow<List<Transaction>> = flow
    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> =
        MutableStateFlow(flow.value.map { CategorizedTransaction(it, categoryName = null) })

    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = flow
    override fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>> =
        getAllCategorized()

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = flow
    override suspend fun getById(id: TransactionId): Transaction? = flow.value.firstOrNull { it.id == id }
    override suspend fun insert(transaction: Transaction) {
        flow.value = flow.value + transaction
    }

    override suspend fun update(transaction: Transaction) {
        flow.value = flow.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(id: TransactionId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

internal class FakeWorkspaceRepository(initial: List<Workspace> = emptyList()) : WorkspaceRepository {
    private val flow = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Workspace>> = flow
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flow
    override suspend fun getById(id: WorkspaceId): Workspace? = flow.value.firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) {
        flow.value = flow.value + workspace
    }

    override suspend fun update(workspace: Workspace) {
        flow.value = flow.value.map { if (it.id == workspace.id) workspace else it }
    }

    override suspend fun delete(id: WorkspaceId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

internal class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val flow = MutableStateFlow(initial)

    fun emit(categories: List<Category>) {
        flow.value = categories
    }

    override fun getAll(): Flow<List<Category>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flow
    override suspend fun getById(id: CategoryId): Category? = flow.value.firstOrNull { it.id == id }
    override suspend fun insert(category: Category) {
        flow.value = flow.value + category
    }

    override suspend fun update(category: Category) {
        flow.value = flow.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: CategoryId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}
