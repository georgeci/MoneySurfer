package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

/**
 * Applies [edit] to the stored account. The caller never assembles the row itself — currency,
 * balance and archive state are owned by other flows, so an editor that copied a stale snapshot
 * back could roll them back.
 */
@Single
class UpdateAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(
        id: AccountId,
        edit: (Account) -> Account,
    ): Either<AccountActionError, Unit> = either {
        val existing = accountRepository.getById(id) ?: raise(AccountActionError.AccountNotFound)
        Either.catch { accountRepository.update(edit(existing)) }
            .mapLeft { AccountActionError.LocalWriteFailed(it) }
            .bind()
    }
}
