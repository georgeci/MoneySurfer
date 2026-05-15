package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Typed failure for [UpdateWorkspaceCurrencyUseCase].
 */
sealed interface UpdateWorkspaceCurrencyError {
    /** No workspace exists for the supplied id — caller should not have reached the picker. */
    data object WorkspaceNotFound : UpdateWorkspaceCurrencyError

    /** A Room write failed mid-update. The workspace currency may be partially applied. */
    data class WriteFailed(val cause: Throwable) : UpdateWorkspaceCurrencyError
}

/**
 * Sets the workspace base currency and re-currencies any account still on the previous
 * (seeded) currency, so the Dashboard total — which reads `Account.currencyCode` — reflects
 * the user's choice. Used by the first-run currency picker; the offline seed creates exactly
 * one "Cash" account, so in practice this updates that single row.
 *
 * No-op when the requested currency already matches.
 */
@Single
class UpdateWorkspaceCurrencyUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val accountRepository: AccountRepository,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(
        workspaceId: WorkspaceId,
        currency: CurrencyCode,
    ): Either<UpdateWorkspaceCurrencyError, Unit> = either {
        val workspace = workspaceRepository.getById(workspaceId)
            ?: raise(UpdateWorkspaceCurrencyError.WorkspaceNotFound)
        val previous = workspace.baseCurrency
        if (previous == currency) {
            log.d { "[skip] currency unchanged wid=${workspaceId.value} currency=${currency.value}" }
            return@either
        }
        Either
            .catch {
                workspaceRepository.update(workspace.copy(baseCurrency = currency))
                accountRepository.getByWorkspaceId(workspaceId).first()
                    .filter { it.currencyCode == previous }
                    .forEach { accountRepository.update(it.copy(currencyCode = currency)) }
            }
            .onLeft { log.e(it) { "[update] failed wid=${workspaceId.value}" } }
            .mapLeft { UpdateWorkspaceCurrencyError.WriteFailed(it) }
            .bind()
        log.i { "[done] wid=${workspaceId.value} currency=${previous.value}->${currency.value}" }
    }

    private companion object {
        const val TAG = "UpdateWorkspaceCurrency"
    }
}
