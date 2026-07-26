package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetBudgetsUseCase(
    private val budgetRepository: BudgetRepository,
    private val session: SessionPointers,
) {

    /** Every budget of the active workspace, archived ones included — the caller splits them. */
    operator fun invoke(): Flow<List<Budget>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId?.let(budgetRepository::getByWorkspaceId) ?: flowOf(emptyList())
        }
}
