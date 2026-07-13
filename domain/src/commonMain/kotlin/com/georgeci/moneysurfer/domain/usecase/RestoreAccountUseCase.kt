package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

/**
 * Reverses [ArchiveAccountUseCase] — moves the account back to the active list.
 */
@Single
class RestoreAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(id: AccountId): Either<AccountActionError, Unit> = either {
        val existing = accountRepository.getById(id) ?: raise(AccountActionError.AccountNotFound)
        if (!existing.archived) return@either
        Either.catch { accountRepository.setArchived(id, archived = false) }
            .mapLeft { AccountActionError.LocalWriteFailed(it) }
            .bind()
    }
}
