package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetProgress
import com.georgeci.moneysurfer.domain.model.calculateBudgetProgress
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * Live progress for one budget or a whole list of them.
 *
 * [progressOf] takes the budgets as an argument rather than reading them itself so the list
 * screen can share a single transaction subscription across every card — one query instead
 * of one per budget.
 */
@Single
class GetBudgetProgressUseCase(
    private val transactionRepository: TransactionRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        budget: Budget,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<BudgetProgress> =
        progressOf(budget.workspaceId, listOf(budget), timeZone).map { it.first() }

    fun progressOf(
        workspaceId: WorkspaceId,
        budgets: List<Budget>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<List<BudgetProgress>> = flow {
        val baseCurrency = workspaceRepository.getById(workspaceId)?.baseCurrency
        emitAll(
            transactionRepository.getByWorkspaceId(workspaceId).map { transactions ->
                val today = clock.now().toLocalDateTime(timeZone).date
                budgets.map { budget ->
                    calculateBudgetProgress(budget, transactions, baseCurrency, today)
                }
            },
        )
    }
}
