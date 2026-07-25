package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Transaction
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

    operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<Transaction>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId
                ?.let(transactionRepository::getByWorkspaceId)
                ?.map { it.take(limit) }
                ?: flowOf(emptyList())
        }

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
