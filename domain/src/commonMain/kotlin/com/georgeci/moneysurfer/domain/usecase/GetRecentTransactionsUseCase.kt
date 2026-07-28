package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.TransactionSplitGroup
import com.georgeci.moneysurfer.domain.model.groupSplitLegs
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetRecentTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
    private val session: SessionPointers,
) {

    /**
     * The newest [limit] *receipts* of the current workspace, newest first.
     *
     * Receipts, not rows: the legs of a split are collapsed into one group, so a supermarket run
     * filed under three categories takes one line of the recent-activity widget rather than
     * crowding out everything else that happened that day.
     *
     * Grouping happens before the limit is applied, over the workspace's whole list, so a group is
     * always complete here — nothing can be cut in half by the cap the way a `take` on raw rows
     * would do it.
     */
    operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<TransactionSplitGroup>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId
                ?.let(transactionRepository::getByWorkspaceId)
                ?.map { rows -> rows.groupSplitLegs().take(limit) }
                ?: flowOf(emptyList())
        }

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
