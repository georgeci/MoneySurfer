package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserAccountDeletionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

class DeleteUserAccountUseCaseTest : StringSpec({

    "happy path runs steps in order and clears the local session" {
        val env = DeletionEnv()

        val result = env.useCase(password = "secret")

        result shouldBe Unit.right()
        env.calls shouldBe listOf("reauth", "shutdown", "remoteCleanup", "authDelete", "localReset", "ledgerReset")
        env.session.currentUserId.first() shouldBe null
        env.session.currentWorkspaceId.first() shouldBe null
        env.session.currentFirebaseUid.first() shouldBe null
    }

    "anonymous flow (null password) skips re-auth" {
        val env = DeletionEnv()

        val result = env.useCase(password = null)

        result shouldBe Unit.right()
        env.calls.first() shouldBe "shutdown"
        env.calls.contains("reauth") shouldBe false
    }

    "no active session returns NotSignedIn without touching anything" {
        val env = DeletionEnv(currentUid = null)

        val result = env.useCase(password = null)

        result shouldBe AccountDeletionError.NotSignedIn.left()
        env.calls shouldBe emptyList()
    }

    "wrong password aborts before shutdown and cleanup" {
        val env = DeletionEnv(
            reauthResult = AuthError(AuthError.Type.InvalidCredentials).left(),
        )

        val result = env.useCase(password = "wrong")

        result shouldBe AccountDeletionError.InvalidCredentials.left()
        env.calls shouldBe listOf("reauth")
        env.session.currentUserId.first() shouldBe USER_ID
    }

    "remote cleanup failure keeps the local session so the user can retry" {
        val env = DeletionEnv(
            cleanupResult = AccountDeletionError.RemoteDataCleanupFailed().left(),
        )

        val result = env.useCase(password = "secret")

        result.shouldBeInstanceOf<Either.Left<AccountDeletionError.RemoteDataCleanupFailed>>()
        env.calls shouldBe listOf("reauth", "shutdown", "remoteCleanup")
        env.session.currentUserId.first() shouldBe USER_ID
    }

    "stale auth session surfaces RequiresRecentLogin after cleanup" {
        val env = DeletionEnv(
            authDeleteResult = AuthError(AuthError.Type.RequiresRecentLogin).left(),
        )

        val result = env.useCase(password = null)

        result shouldBe AccountDeletionError.RequiresRecentLogin.left()
        env.calls shouldBe listOf("shutdown", "remoteCleanup", "authDelete")
        env.session.currentUserId.first() shouldBe USER_ID
    }
})

private val USER_ID = UserId("uid-1")

private class DeletionEnv(
    currentUid: String? = "uid-1",
    val reauthResult: Either<AuthError, Unit> = Unit.right(),
    val cleanupResult: Either<AccountDeletionError, Unit> = Unit.right(),
    val authDeleteResult: Either<AuthError, Unit> = Unit.right(),
) {
    val calls = mutableListOf<String>()
    val session = InMemorySessionPointers(
        currentUserId = if (currentUid == null) null else USER_ID,
        currentFirebaseUid = currentUid,
    )

    private val auth = object : AuthRemoteRepository {
        override fun currentUid(): String? = currentUid
        override fun currentEmail(): String? = currentUid?.let { "user@example.com" }
        override fun isCurrentUserAnonymous(): Boolean = false
        override suspend fun signInWithEmail(email: String, password: String) = error("unused")
        override suspend fun createUserWithEmail(email: String, password: String) = error("unused")
        override suspend fun signInAnonymously() = error("unused")
        override suspend fun signOut() = error("unused")
        override suspend fun reauthenticateWithEmail(email: String, password: String): Either<AuthError, Unit> {
            calls += "reauth"
            return reauthResult
        }

        override suspend fun deleteCurrentUser(): Either<AuthError, Unit> {
            calls += "authDelete"
            return authDeleteResult
        }
    }

    val useCase = DeleteUserAccountUseCase(
        sessionShutdownGate = object : SessionShutdownGate {
            override suspend fun shutdown() {
                calls += "shutdown"
            }
        },
        authRemoteRepository = auth,
        userAccountDeletionRepository = object : UserAccountDeletionRepository {
            override suspend fun deleteRemoteUserData(
                uid: String,
                email: String?,
            ): Either<AccountDeletionError, Unit> {
                calls += "remoteCleanup"
                return cleanupResult
            }
        },
        localDataResetRepository = object : LocalDataResetRepository {
            override suspend fun clearAll() {
                calls += "localReset"
            }
        },
        remoteDataResetRepository = object : RemoteDataResetRepository {
            override suspend fun clearAll() {
                calls += "ledgerReset"
            }
        },
        sessionMutator = session,
    )
}
