package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import org.koin.core.annotation.Single

@Single
class LoginUseCase(
    private val authRemoteRepository: AuthRemoteRepository,
    private val authLocalRepository: AuthLocalRepository,
    private val sessionMutator: SessionMutator,
    private val wipeDemoDataUseCase: WipeDemoDataUseCase,
    private val postAuthBootstrap: PostAuthBootstrapUseCase,
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
        sessionMutator.setFirebaseUid(uid)

        postAuthBootstrap(
            uid = uid,
            email = trimmed,
            displayName = displayName,
            isAnon = false,
        ).bind()
    }
}
