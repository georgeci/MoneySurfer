package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

/**
 * Hard delete — the row and its sync tombstone. The reversible "get it out of my way" path is
 * [ArchiveAccountUseCase], which keeps the account and its transactions.
 */
@Single
class DeleteAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(id: AccountId): Either<AccountActionError, Unit> = either {
        accountRepository.getById(id) ?: raise(AccountActionError.AccountNotFound)
        Either.catch { accountRepository.delete(id) }
            .mapLeft { AccountActionError.LocalWriteFailed(it) }
            .bind()
    }
}
