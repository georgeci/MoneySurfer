package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.transactionsIn
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.windowContaining
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * The transactions that make up a budget's spend in its current window — the same filter
 * [GetBudgetProgressUseCase] sums, so the list always adds up to the number above it.
 */
@Single
class GetBudgetTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        budget: Budget,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<List<CategorizedTransaction>> = flow {
        val baseCurrency = workspaceRepository.getById(budget.workspaceId)?.baseCurrency
        emitAll(
            transactionRepository.getAllCategorized().map { categorized ->
                val window = budget.windowContaining(clock.now().toLocalDateTime(timeZone).date)
                val matching = budget.transactionsIn(
                    transactions = categorized.map { it.transaction },
                    window = window,
                    baseCurrency = baseCurrency,
                ).map { it.id }.toSet()
                categorized.filter { it.transaction.id in matching }
                    .sortedByDescending { it.transaction.operationDate }
            },
        )
    }
}
