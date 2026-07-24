package com.georgeci.moneysurfer.data.repository

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
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

    override suspend fun reauthenticateWithEmail(email: String, password: String): Either<AuthError, Unit> =
        Either.catch {
            val user = auth.currentUser ?: error("No active session to re-authenticate")
            user.reauthenticate(EmailAuthProvider.credential(email.trim(), password))
        }.mapLeft { it.toAuthError() }

    override suspend fun deleteCurrentUser(): Either<AuthError, Unit> =
        Either.catch {
            val user = auth.currentUser ?: error("No active session to delete")
            user.delete()
        }.mapLeft { it.toAuthError() }
}

/** Classify Firebase auth exceptions into the typed [AuthError.Type] domain enum. */
private fun Throwable.toAuthError(): AuthError {
    val byMessage = classifyAuthErrorByMessage(message)
    val type = when {
        // Checked before the typed branches on purpose: Android throws a plain
        // FirebaseAuthInvalidCredentialsException for a malformed address, which would otherwise
        // surface as "wrong email or password" — nonsense while creating an account.
        byMessage == AuthError.Type.InvalidEmail -> AuthError.Type.InvalidEmail
        this is FirebaseAuthRecentLoginRequiredException -> AuthError.Type.RequiresRecentLogin
        this is FirebaseAuthWeakPasswordException -> AuthError.Type.WeakPassword
        this is FirebaseAuthUserCollisionException -> AuthError.Type.EmailAlreadyInUse
        this is FirebaseAuthInvalidCredentialsException -> AuthError.Type.InvalidCredentials
        this is FirebaseAuthInvalidUserException -> AuthError.Type.InvalidCredentials
        // gitlive's iOS SDK surfaces every FIRAuthErrorDomain failure as the base
        // FirebaseAuthException rather than the typed subclasses matched above, so
        // wrong-password / no-such-user sign-ins fall through here and would show a
        // generic "Sign-in failed" instead of "Wrong email or password" (issue
        // #219). Recover the classification from the server error-code name carried
        // in the message. Android throws the typed subclasses and is matched above,
        // so this fallback only ever runs on iOS.
        else -> byMessage ?: AuthError.Type.Unknown
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
        // Must precede the invalid-credential codes: ERROR_INVALID_EMAIL is the more specific
        // diagnosis and Android reports it only through the human-readable phrase.
        message.contains("ERROR_INVALID_EMAIL", ignoreCase = true) ||
            message.contains("badly formatted", ignoreCase = true) ->
            AuthError.Type.InvalidEmail
        INVALID_CREDENTIAL_ERROR_CODES.any { message.contains(it, ignoreCase = true) } ->
            AuthError.Type.InvalidCredentials
        message.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) ->
            AuthError.Type.EmailAlreadyInUse
        message.contains("ERROR_WEAK_PASSWORD", ignoreCase = true) ->
            AuthError.Type.WeakPassword
        else -> null
    }
}
