package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserAccountDeletionRepository
import org.koin.core.annotation.Single

/**
 * Permanently deletes the signed-in user: their Firestore footprint, the Firebase Auth user,
 * and every trace on this device (issue #213; semantics match the hosted account-deletion page).
 *
 * Step order is load-bearing:
 *  1. re-auth first, so a wrong password aborts before anything is touched;
 *  2. sync shutdown before remote cleanup, so an in-flight push can't recreate purged docs;
 *  3. Firestore cleanup while the auth user still exists — rules authorize each delete
 *     against `request.auth.uid`;
 *  4. auth-user deletion is the point of no return;
 *  5. local teardown last, mirroring [LogoutUseCase] — clearing `currentUserId` is also what
 *     bounces navigation to SignIn (see AppLaunchViewModel).
 *
 * On failure the local session is intentionally left intact so the user can retry; every
 * remote step is idempotent.
 */
@Single
class DeleteUserAccountUseCase(
    private val sessionShutdownGate: SessionShutdownGate,
    private val authRemoteRepository: AuthRemoteRepository,
    private val userAccountDeletionRepository: UserAccountDeletionRepository,
    private val localDataResetRepository: LocalDataResetRepository,
    private val remoteDataResetRepository: RemoteDataResetRepository,
    private val session: SessionPointers,
) {

    /**
     * [password] re-authenticates email/password sessions; pass null for anonymous sessions
     * (they cannot re-auth — a stale one surfaces [AccountDeletionError.RequiresRecentLogin]).
     */
    suspend operator fun invoke(password: String?): Either<AccountDeletionError, Unit> = either {
        val uid = ensureNotNull(authRemoteRepository.currentUid()) { AccountDeletionError.NotSignedIn }

        if (password != null) reauthenticate(password).bind()

        sessionShutdownGate.shutdown()

        userAccountDeletionRepository
            .deleteRemoteUserData(uid = uid, email = authRemoteRepository.currentEmail())
            .bind()

        authRemoteRepository.deleteCurrentUser()
            .mapLeft { it.toDeleteError() }
            .bind()

        session.currentUserId.set(null)
        session.currentWorkspaceId.set(null)
        session.currentFirebaseUid.set(null)
        localDataResetRepository.clearAll()
        remoteDataResetRepository.clearAll()
    }

    private suspend fun reauthenticate(password: String): Either<AccountDeletionError, Unit> {
        val email = authRemoteRepository.currentEmail()
            ?: return AccountDeletionError.NotSignedIn.left()
        return authRemoteRepository.reauthenticateWithEmail(email, password)
            .mapLeft { it.toReauthError() }
    }

    private fun AuthError.toReauthError(): AccountDeletionError = when (type) {
        AuthError.Type.InvalidCredentials -> AccountDeletionError.InvalidCredentials
        else -> AccountDeletionError.ReauthenticationFailed(cause = cause)
    }

    private fun AuthError.toDeleteError(): AccountDeletionError = when (type) {
        AuthError.Type.RequiresRecentLogin -> AccountDeletionError.RequiresRecentLogin
        else -> AccountDeletionError.AuthDeletionFailed(cause = cause)
    }
}
