package com.georgeci.moneysurfer.offline.noop

import arrow.core.Either
import arrow.core.left
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository

/**
 * Offline build has no Firebase Auth. Reads return empty session state; any
 * write call (sign-in, sign-up, sign-out) reports `Unknown` so the UI surfaces
 * a generic auth error rather than crashing on a missing definition.
 */
class NoOpAuthRemoteRepository : AuthRemoteRepository {
    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false

    override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, String> =
        unsupported()

    override suspend fun createUserWithEmail(email: String, password: String): Either<AuthError, String> =
        unsupported()

    override suspend fun signInAnonymously(): Either<AuthError, String> = unsupported()

    override suspend fun signOut(): Either<AuthError, Unit> =
        AuthError(AuthError.Type.Unknown, "Auth disabled in offline build").left()

    private fun unsupported(): Either<AuthError, String> =
        AuthError(AuthError.Type.Unknown, "Auth disabled in offline build").left()
}
