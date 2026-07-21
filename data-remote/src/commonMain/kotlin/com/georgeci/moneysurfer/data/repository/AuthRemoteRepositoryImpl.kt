package com.georgeci.moneysurfer.data.repository

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import org.koin.core.annotation.Single

@Single(binds = [AuthRemoteRepository::class])
class AuthRemoteRepositoryImpl(
    private val auth: FirebaseAuth,
) : AuthRemoteRepository {

    override fun currentUid(): String? = auth.currentUser?.uid

    override fun currentEmail(): String? = auth.currentUser?.email

    override fun isCurrentUserAnonymous(): Boolean = auth.currentUser?.isAnonymous == true

    override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, String> =
        Either.catch { auth.signInWithEmailAndPassword(email.trim(), password).user!!.uid }
            .mapLeft { it.toAuthError() }

    override suspend fun createUserWithEmail(email: String, password: String): Either<AuthError, String> =
        Either.catch { auth.createUserWithEmailAndPassword(email.trim(), password).user!!.uid }
            .mapLeft { it.toAuthError() }

    override suspend fun signInAnonymously(): Either<AuthError, String> =
        Either.catch { auth.currentUser?.uid ?: auth.signInAnonymously().user!!.uid }
            .mapLeft { it.toAuthError() }

    override suspend fun signOut(): Either<AuthError, Unit> =
        Either.catch { auth.signOut() }.mapLeft { it.toAuthError() }
}

/** Classify Firebase auth exceptions into the typed [AuthError.Type] domain enum. */
private fun Throwable.toAuthError(): AuthError {
    val type = when (this) {
        is FirebaseAuthWeakPasswordException -> AuthError.Type.WeakPassword
        is FirebaseAuthUserCollisionException -> AuthError.Type.EmailAlreadyInUse
        is FirebaseAuthInvalidCredentialsException -> AuthError.Type.InvalidCredentials
        is FirebaseAuthInvalidUserException -> AuthError.Type.InvalidCredentials
        // gitlive's iOS SDK surfaces every FIRAuthErrorDomain failure as the base
        // FirebaseAuthException rather than the typed subclasses matched above, so
        // wrong-password / no-such-user sign-ins fall through here and would show a
        // generic "Sign-in failed" instead of "Wrong email or password" (issue
        // #219). Recover the classification from the server error-code name carried
        // in the message. Android throws the typed subclasses and is matched above,
        // so this fallback only ever runs on iOS.
        else -> classifyAuthErrorByMessage(message) ?: AuthError.Type.Unknown
    }
    return AuthError(type = type, message = message, cause = this)
}

// FIRAuth error-code names that all mean "the supplied credentials don't match a
// user" — spelled identically to Android's FirebaseAuthException.getErrorCode().
private val INVALID_CREDENTIAL_ERROR_CODES = listOf(
    "ERROR_USER_NOT_FOUND",
    "ERROR_WRONG_PASSWORD",
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_INVALID_LOGIN_CREDENTIALS",
)

/**
 * Fallback classifier keyed off the Firebase error-code name embedded in a
 * [dev.gitlive.firebase.auth.FirebaseAuthException] message. Needed only for
 * gitlive's iOS SDK, which does not throw the typed exception subclasses (see
 * [toAuthError]). Returns null when no known code is present so the caller falls
 * back to [AuthError.Type.Unknown].
 */
internal fun classifyAuthErrorByMessage(message: String?): AuthError.Type? {
    if (message == null) return null
    return when {
        INVALID_CREDENTIAL_ERROR_CODES.any { message.contains(it, ignoreCase = true) } ->
            AuthError.Type.InvalidCredentials
        message.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) ->
            AuthError.Type.EmailAlreadyInUse
        message.contains("ERROR_WEAK_PASSWORD", ignoreCase = true) ->
            AuthError.Type.WeakPassword
        else -> null
    }
}
