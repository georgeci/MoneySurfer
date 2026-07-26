package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.constants.PREFILLED_DEFAULT_USER_ID
import org.koin.core.annotation.Single

/**
 * Local-only "demo" entry — skips Firebase entirely. Pins a deterministic local user id so the
 * demo flow can repeatedly re-enter without piling up user rows.
 *
 * Sets `SessionPointers.hasUsedDemo` so a subsequent real login/signup can wipe the demo data
 * before bootstrapping the real session — demo data must never reach Firestore. See sync.md §2.11.
 */
@Single
class DemoLoginUseCase(
    private val authLocalRepository: AuthLocalRepository,
    private val sessionMutator: SessionMutator,
) {
    suspend operator fun invoke(): Either<AuthError, Unit> =
        Either
            .catch {
                authLocalRepository.createLocalUser(
                    uid = PREFILLED_DEFAULT_USER_ID,
                    email = null,
                    displayName = "Demo",
                    isAnon = false,
                )
                sessionMutator.setHasUsedDemo(true)
            }
            .mapLeft { AuthError(type = AuthError.Type.Unknown, message = it.message, cause = it) }
}
