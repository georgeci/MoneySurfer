package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

/**
 * Adds a new account. The opening balance is a separate `OPENING_BALANCE` transaction — the row
 * itself is always created at zero so the cached balance stays a projection of the ledger.
 */
@Single
class CreateAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(account: Account): Either<AccountActionError, Unit> = either {
        Either.catch { accountRepository.insert(account) }
            .mapLeft { AccountActionError.LocalWriteFailed(it) }
            .bind()
    }
}
