package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * One-shot bootstrap for an empty install: creates a "Personal" workspace (which seeds the
 * default category set via [CreateWorkspaceUseCase]) and a single "Cash" account.
 *
 * Idempotency contract:
 *  - If a workspace is already pinned in [SessionPointers.currentWorkspaceId], do nothing.
 *  - If the freshly created workspace already has a "Cash" account (e.g. previous run partially
 *    completed and pinned the workspace before inserting the account), do not duplicate it.
 *
 * The use case is host-agnostic — the caller supplies the [CurrencyCode] for the workspace and
 * seed account so platform locale lookup stays out of the domain layer.
 */
@Single
class SeedDefaultsUseCase(
    private val createWorkspace: CreateWorkspaceUseCase,
    private val accountRepository: AccountRepository,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(currency: CurrencyCode) {
        val pinned = session.currentWorkspaceId.flow.first()
        if (pinned != null) {
            log.d { "[skip] workspace already pinned wid=${pinned.value}" }
            return
        }
        log.i { "[start] currency=${currency.value}" }
        val newId = createWorkspace(
            CreateWorkspaceUseCase.Params(
                name = DEFAULT_WORKSPACE_NAME,
                description = "",
                baseCurrency = currency,
            ),
        ).fold(
            ifLeft = { err ->
                log.w { "[abort] CreateWorkspace failed: $err" }
                return
            },
            ifRight = { it },
        )
        seedCashAccountIfMissing(newId, currency)
    }

    private suspend fun seedCashAccountIfMissing(workspaceId: WorkspaceId, currency: CurrencyCode) {
        val existing = Either.catch { accountRepository.getByWorkspaceId(workspaceId).first() }
            .getOrNull()
            .orEmpty()
        val alreadyHasCash = existing.any { it.name == DEFAULT_ACCOUNT_NAME && it.type == AccountType.CASH }
        if (alreadyHasCash) {
            log.d { "[skip] Cash account already present wid=${workspaceId.value}" }
            return
        }
        accountRepository.insert(
            Account(
                id = AccountId.uuid(),
                workspaceId = workspaceId,
                name = DEFAULT_ACCOUNT_NAME,
                type = AccountType.CASH,
                currencyCode = currency,
                balance = Money.zero(),
                updatedAt = getCurrentTime(),
            ),
        )
        log.i { "[done] seeded Cash account wid=${workspaceId.value}" }
    }

    private companion object {
        const val TAG = "SeedDefaults"
        const val DEFAULT_WORKSPACE_NAME = "Personal"
        const val DEFAULT_ACCOUNT_NAME = "Cash"
    }
}
