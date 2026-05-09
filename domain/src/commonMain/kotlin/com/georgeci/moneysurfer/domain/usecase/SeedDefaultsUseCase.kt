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
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Bootstrap for an empty install: creates a "Personal" workspace (which seeds the default
 * category set via [CreateWorkspaceUseCase]) and a single "Cash" account.
 *
 * Idempotency contract — the use case is safe to call on every launch:
 *  - If no workspace is pinned, create one + seed Cash.
 *  - If a workspace is already pinned, *repair* missing defaults — i.e. ensure the pinned
 *    workspace has a Cash account. This recovers from a previous run that pinned the workspace
 *    but died before inserting the account, instead of stranding the user on a Dashboard with
 *    no accounts forever (Copilot review feedback on PR #91).
 *  - Existing Cash accounts are never duplicated.
 *
 * Insert failures are caught and logged so a transient repository error doesn't block the
 * caller (e.g. `AppLaunchViewModel`) from making a navigation decision.
 *
 * The use case is host-agnostic — the caller supplies the seed [CurrencyCode] so platform
 * locale lookup stays out of the domain layer.
 */
@Single
class SeedDefaultsUseCase(
    private val createWorkspace: CreateWorkspaceUseCase,
    private val accountRepository: AccountRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(currency: CurrencyCode) {
        val pinned = session.currentWorkspaceId.flow.first()
        if (pinned != null) {
            // Repair path: previous run may have pinned the workspace but died before the Cash
            // insert. Use the workspace's own baseCurrency for repair so we don't overwrite the
            // user's currency choice with a freshly derived locale value.
            val workspaceCurrency = runCatching { workspaceRepository.getById(pinned)?.baseCurrency }
                .getOrNull() ?: currency
            seedCashAccountIfMissing(pinned, workspaceCurrency)
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
        Either.catch {
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
        }
            .onLeft { log.w(it) { "[insert] Cash account failed wid=${workspaceId.value}" } }
            .onRight { log.i { "[done] seeded Cash account wid=${workspaceId.value}" } }
    }

    private companion object {
        const val TAG = "SeedDefaults"
        const val DEFAULT_WORKSPACE_NAME = "Personal"
        const val DEFAULT_ACCOUNT_NAME = "Cash"
    }
}
