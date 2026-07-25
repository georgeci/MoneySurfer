package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

/**
 * Persists the order the user dragged the accounts into. [orderedIds] is the full list of the
 * accounts being ordered, first to last; the repository turns it into `sortOrder` values and
 * syncs the rows that moved.
 */
@Single
class ReorderAccountsUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(orderedIds: List<AccountId>): Either<AccountActionError, Unit> =
        Either.catch { accountRepository.reorder(orderedIds) }
            .mapLeft { AccountActionError.LocalWriteFailed(it) }
}
