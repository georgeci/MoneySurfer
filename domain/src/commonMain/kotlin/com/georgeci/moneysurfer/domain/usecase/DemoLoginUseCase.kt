package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.config.SyncedSettingsSession
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
    private val syncedSettingsSession: SyncedSettingsSession,
) {
    suspend operator fun invoke(): Either<AuthError, Unit> =
        Either
            .catch {
                // A demo session is a session: the previous user's settings overlay must not follow
                // them into it. The reconciliation half no-ops here — there is no Firebase uid.
                //
                // Swallowed rather than folded into the result, matching `PostAuthBootstrapUseCase`:
                // failing to tidy the settings overlay is not a reason to refuse entry to a
                // local-only demo, and the next session start retries it.
                Either.catch { syncedSettingsSession.onSessionStart() }
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
