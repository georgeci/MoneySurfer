package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.transactionsIn
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.BudgetPeriodWindow
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.windowContaining
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * A budget window holds one period of spend, so this bound is far above any realistic count —
 * it exists only because the query is paged, and cuts off the oldest rows if it is ever hit.
 */
private const val WINDOW_TRANSACTION_LIMIT = 1000

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
        val window = budget.windowContaining(clock.now().toLocalDateTime(timeZone).date)
        emitAll(
            transactionRepository.getCategorizedWindow(
                accountId = null,
                window = window.asTransactionPeriod(),
                limit = WINDOW_TRANSACTION_LIMIT,
            ).map { categorized ->
                // The date range is already applied by the query; the rest of the budget filter
                // (workspace, type, status, transfers, categories, currency) is not.
                val matching = budget.transactionsIn(
                    transactions = categorized.map { it.transaction },
                    window = window,
                    baseCurrency = baseCurrency,
                ).map { it.id }.toSet()
                categorized.filter { it.transaction.id in matching }
            },
        )
    }
}

/**
 * The same span as a transaction-query window: a budget window is half-open, the query one is
 * closed, so the last day moves in by one.
 */
private fun BudgetPeriodWindow.asTransactionPeriod(): TransactionPeriodWindow =
    TransactionPeriodWindow(
        from = startInclusive,
        to = endExclusive.minus(1, DateTimeUnit.DAY),
    )
