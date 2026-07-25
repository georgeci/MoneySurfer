package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import org.koin.core.annotation.Single

@Single
@Suppress("LongParameterList")
class LoginUseCase(
    private val authRemoteRepository: AuthRemoteRepository,
    private val authLocalRepository: AuthLocalRepository,
    private val session: SessionPointers,
    private val wipeDemoDataUseCase: WipeDemoDataUseCase,
    private val postAuthBootstrap: PostAuthBootstrapUseCase,
    private val abandonAuthSession: AbandonAuthSessionUseCase,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): Either<AuthError, PostAuthBootstrapUseCase.Result> = either {
        val trimmed = email.trim()
        val displayName = trimmed.ifBlank { null }

        val uid = authRemoteRepository.signInWithEmail(trimmed, password).bind()

        // Drop any demo data BEFORE pinning the real session — demo rows must
        // never end up in the real account's outbox / Firestore (sync.md §2.11).
        wipeDemoDataUseCase()

        authLocalRepository.createLocalUser(
            uid = uid,
            email = trimmed,
            displayName = displayName,
            isAnon = false,
        )
        session.currentFirebaseUid.set(uid)

        // The pointers have to be live *before* the bootstrap — the pull reads
        // `currentFirebaseUid` to discover the user's workspaces — so atomicity is bought with a
        // rollback rather than by deferring the writes. Without it a failed bootstrap leaves a
        // signed-in session with no workspace, which the next launch routes past sign-in
        // straight into the selector with no way back (issue #342).
        postAuthBootstrap(
            uid = uid,
            email = trimmed,
            displayName = displayName,
            isAnon = false,
        ).onLeft { abandonAuthSession(isAnon = false) }.bind()
    }
}
