package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetAccountsUseCase(
    private val accountRepository: AccountRepository,
    private val session: SessionPointers,
) {

    operator fun invoke(): Flow<List<Account>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId?.let(accountRepository::getByWorkspaceId) ?: flowOf(emptyList())
        }
}
