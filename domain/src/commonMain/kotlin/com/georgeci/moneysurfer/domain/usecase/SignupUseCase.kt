package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import org.koin.core.annotation.Single

@Single
@Suppress("LongParameterList")
class SignupUseCase(
    private val authRemoteRepository: AuthRemoteRepository,
    private val authLocalRepository: AuthLocalRepository,
    private val sessionMutator: SessionMutator,
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

        val uid = authRemoteRepository.createUserWithEmail(trimmed, password).bind()

        // Drop any demo data BEFORE pinning the real session (sync.md §2.11).
        wipeDemoDataUseCase()

        authLocalRepository.createLocalUser(
            uid = uid,
            email = trimmed,
            displayName = displayName,
            isAnon = false,
        )
        sessionMutator.setFirebaseUid(uid)

        // Roll the pointers back when the bootstrap fails — see [LoginUseCase] for why the
        // pins cannot simply be deferred until after it (issue #342).
        postAuthBootstrap(
            uid = uid,
            email = trimmed,
            displayName = displayName,
            isAnon = false,
        ).onLeft { abandonAuthSession(isAnon = false) }.bind()
    }
}
