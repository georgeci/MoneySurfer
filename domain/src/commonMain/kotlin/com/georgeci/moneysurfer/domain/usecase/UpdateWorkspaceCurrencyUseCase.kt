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
 * Brings the workspace and every account it owns onto [currency], so the Dashboard total —
 * which reads `Account.currencyCode` — reflects the user's choice. Used by the first-run
 * currency picker; the offline seed creates exactly one "Cash" account, so in practice this
 * updates that single row.
 *
 * Each step is idempotent and reconciles against the *target* currency rather than the
 * workspace's previous value: a retry after a partial failure (workspace updated but an
 * account write failed) still repairs the lagging accounts instead of short-circuiting on
 * the already-migrated workspace.
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
        Either
            .catch {
                if (workspace.baseCurrency != currency) {
                    workspaceRepository.update(workspace.copy(baseCurrency = currency))
                }
                accountRepository.getByWorkspaceId(workspaceId).first()
                    .filter { it.currencyCode != currency }
                    .forEach { accountRepository.update(it.copy(currencyCode = currency)) }
            }
            .onLeft { log.e(it) { "[update] failed wid=${workspaceId.value}" } }
            .mapLeft { UpdateWorkspaceCurrencyError.WriteFailed(it) }
            .bind()
        log.i { "[done] wid=${workspaceId.value} currency=${currency.value}" }
    }

    private companion object {
        const val TAG = "UpdateWorkspaceCurrency"
    }
}
