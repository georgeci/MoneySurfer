package com.georgeci.moneysurfer.domain.repositories

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.AuthError

/**
 * Auth-provider gateway. The data-layer implementation wraps Firebase Auth and classifies
 * provider-specific exceptions into [AuthError.Type] before crossing the module boundary, so
 * the domain stays free of any third-party auth SDK types.
 */
interface AuthRemoteRepository {
    /** Returns the uid of the already-signed-in user, or null when no session is active. */
    fun currentUid(): String?

    /** Returns current user's email, or null if unavailable (for example anonymous session). */
    fun currentEmail(): String?

    /** True when current Firebase session is anonymous. */
    fun isCurrentUserAnonymous(): Boolean

    /** Sign in with the email/password provider. Returns the resulting uid. */
    suspend fun signInWithEmail(email: String, password: String): Either<AuthError, String>

    /** Create a new account via the email/password provider. Returns the resulting uid. */
    suspend fun createUserWithEmail(email: String, password: String): Either<AuthError, String>

    /** Anonymous sign-in for "guest" mode. Idempotent: returns the existing uid if any. */
    suspend fun signInAnonymously(): Either<AuthError, String>

    /** Signs the active session out. No-op if there is no active session. */
    suspend fun signOut(): Either<AuthError, Unit>
}
