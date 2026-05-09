package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

/**
 * Marks an account as archived. The account and its transactions stay in the database
 * but are hidden from active lists and excluded from total/budget rollups. Reversible
 * via [RestoreAccountUseCase].
 */
@Single
class ArchiveAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(id: AccountId): Either<AccountActionError, Unit> = either {
        val existing = accountRepository.getById(id) ?: raise(AccountActionError.AccountNotFound)
        if (existing.archived) return@either
        Either.catch { accountRepository.setArchived(id, archived = true) }
            .mapLeft { AccountActionError.LocalWriteFailed(it) }
            .bind()
    }
}
