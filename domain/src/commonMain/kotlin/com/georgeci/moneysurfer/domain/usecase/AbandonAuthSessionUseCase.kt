package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import org.koin.core.annotation.Single

/**
 * Undoes a half-finished sign-in: the auth provider accepted the credentials and the session
 * pointers were already pinned, but [PostAuthBootstrapUseCase] failed afterwards.
 *
 * Without this the app is left "signed in" with no workspace, and `resolveStartRoute` sends the
 * next cold start straight to the workspace selector — the sign-in screen is skipped, and the
 * bootstrap has no retry path of its own (issue #342). Clearing the pointers puts the user back
 * on the sign-in form, where retrying re-runs the whole flow.
 *
 * Deliberately *not* [LogoutUseCase]: nothing was hydrated yet, so there is no local data to
 * wipe and no shutdown gate to trip. The `users` row written by
 * [com.georgeci.moneysurfer.domain.auth.AuthLocalRepository] is left in place — it is an idempotent
 * upsert that the retry rewrites anyway, and deleting it would cascade into rows a later attempt
 * still wants.
 */
@Single
class AbandonAuthSessionUseCase(
    private val sessionMutator: SessionMutator,
    private val authRemoteRepository: AuthRemoteRepository,
) {
    private val log = Logger.withTag(TAG)

    /**
     * [isAnon] must be `true` for an anonymous sign-in. An anonymous account has no credential to
     * sign back in with: `signInAnonymously()` reuses `auth.currentUser` when there is one and
     * mints a brand-new uid otherwise. Signing out would therefore make the *previous* anonymous
     * account — and whatever it already owns on Firestore — permanently unreachable, over what may
     * be a transient bootstrap failure. Clearing the local pointers is enough to put the user back
     * on the sign-in screen; the provider session is left alone so a retry lands on the same uid.
     */
    suspend operator fun invoke(isAnon: Boolean) {
        log.w { "[rollback] clearing session pointers after a failed post-auth bootstrap" }
        // One store edit, so a crash mid-rollback cannot leave a half-cleared session behind —
        // which is the state this use case exists to get out of.
        sessionMutator.clearSession()

        if (isAnon) {
            log.i { "[rollback] keeping the anonymous provider session — signing out would orphan it" }
            return
        }
        // Best-effort: a provider session that outlives the pointers is harmless — the next
        // sign-in overwrites it — but a throw here would mask the original bootstrap failure.
        Either.catch { authRemoteRepository.signOut() }
            .onLeft { log.w(it) { "[rollback] signOut failed — pointers are cleared regardless" } }
    }

    private companion object {
        const val TAG = "AbandonAuthSession"
    }
}
