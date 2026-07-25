package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import org.koin.core.annotation.Single

@Single
class AnonymousLoginUseCase(
    private val authRemoteRepository: AuthRemoteRepository,
    private val authLocalRepository: AuthLocalRepository,
    private val session: SessionPointers,
    private val wipeDemoDataUseCase: WipeDemoDataUseCase,
    private val postAuthBootstrap: PostAuthBootstrapUseCase,
    private val abandonAuthSession: AbandonAuthSessionUseCase,
) {
    suspend operator fun invoke(): Either<AuthError, PostAuthBootstrapUseCase.Result> = either {
        val uid = authRemoteRepository.signInAnonymously().bind()

        // Drop any demo data BEFORE pinning the real session (sync.md §2.11).
        wipeDemoDataUseCase()

        authLocalRepository.createLocalUser(
            uid = uid,
            email = null,
            displayName = null,
            isAnon = true,
        )
        session.currentFirebaseUid.set(uid)

        // Roll the pointers back when the bootstrap fails — see [LoginUseCase] for why the
        // pins cannot simply be deferred until after it (issue #342).
        postAuthBootstrap(
            uid = uid,
            email = null,
            displayName = null,
            isAnon = true,
        ).onLeft { abandonAuthSession(isAnon = true) }.bind()
    }
}
