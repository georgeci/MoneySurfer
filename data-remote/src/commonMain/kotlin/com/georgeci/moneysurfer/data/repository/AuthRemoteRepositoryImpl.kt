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
        else -> AuthError.Type.Unknown
    }
    return AuthError(type = type, message = message, cause = this)
}
