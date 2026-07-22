package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAll(): Flow<List<Budget>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>>
    suspend fun getById(id: BudgetId): Budget?
    suspend fun insert(budget: Budget)
    suspend fun update(budget: Budget)

    /** Archive / restore. Archived budgets stay in the DB but drop out of the main list. */
    suspend fun setActive(id: BudgetId, isActive: Boolean)

    /** Hard delete — the account-purge path. Regular flows archive via [setActive]. */
    suspend fun delete(id: BudgetId)
}
