package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

/**
 * Newest-first transactions booked to [categoryId] **or anything below it**, capped at [limit].
 *
 * Including the subtree matches how the rest of the app treats a parent: the detail screen's
 * headline number already rolls children up, so a recent list that showed only the parent's own
 * transactions would contradict the total sitting directly above it.
 *
 * Filtering happens in memory over the workspace list rather than in SQL. That list is already
 * indexed and sorted for exactly this ordering, and it is the same read the dashboard and the
 * transactions list make — so this shares their cached flow instead of adding a third query
 * shape over the same table.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetTransactionsByCategoryUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val session: SessionPointers,
) {

    operator fun invoke(
        categoryId: CategoryId,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<Transaction>> =
        session.currentWorkspaceId.flow.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(emptyList())

            combine(
                transactionRepository.getByWorkspaceId(workspaceId),
                categoryRepository.getByWorkspaceId(workspaceId),
            ) { transactions, categories ->
                val subtree = CategoryTree.descendantsOf(categories, categoryId) + categoryId
                transactions.asSequence()
                    .filter { it.categoryId in subtree }
                    .take(limit)
                    .toList()
            }
        }

    companion object {
        /** Enough to fill the detail screen's "Recent" block without becoming a second list screen. */
        const val DEFAULT_LIMIT = 10
    }
}
