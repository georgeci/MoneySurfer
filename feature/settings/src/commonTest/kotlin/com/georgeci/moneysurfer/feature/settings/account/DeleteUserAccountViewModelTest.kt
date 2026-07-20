package com.georgeci.moneysurfer.feature.settings.account

import app.cash.turbine.test
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
import com.georgeci.moneysurfer.domain.usecase.DeleteUserAccountUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteUserAccountViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "email user with blank password gets a field error instead of the dialog" {
        val env = VmEnv(isAnonymous = false)

        env.viewModel.onEvent(DeleteUserAccountEvent.OnDeleteClick)

        env.viewModel.currentState.passwordMissing shouldBe true
        env.viewModel.currentState.showConfirmDialog shouldBe false
    }

    "email user with a password reaches the confirmation dialog" {
        val env = VmEnv(isAnonymous = false)

        env.viewModel.onEvent(DeleteUserAccountEvent.OnPasswordChange("secret"))
        env.viewModel.onEvent(DeleteUserAccountEvent.OnDeleteClick)

        env.viewModel.currentState.passwordMissing shouldBe false
        env.viewModel.currentState.showConfirmDialog shouldBe true
    }

    "anonymous user goes straight to the confirmation dialog" {
        val env = VmEnv(isAnonymous = true)

        env.viewModel.currentState.showPasswordField shouldBe false
        env.viewModel.onEvent(DeleteUserAccountEvent.OnDeleteClick)
        env.viewModel.currentState.showConfirmDialog shouldBe true
    }

    "wrong password surfaces WrongPassword notice and re-enables the screen" {
        val env = VmEnv(
            isAnonymous = false,
            reauthResult = AuthError(AuthError.Type.InvalidCredentials).left(),
        )
        env.viewModel.onEvent(DeleteUserAccountEvent.OnPasswordChange("wrong"))
        env.viewModel.onEvent(DeleteUserAccountEvent.OnDeleteClick)

        runTest {
            env.viewModel.sideEffects.effectFlow.test {
                env.viewModel.onEvent(DeleteUserAccountEvent.OnConfirmAccepted)
                awaitItem() shouldBe DeleteUserAccountEffect.Notify(DeleteUserAccountNotice.WrongPassword)
            }
        }
        env.viewModel.currentState.isDeleting shouldBe false
    }

    "cleanup failure surfaces CleanupFailed notice" {
        val env = VmEnv(
            isAnonymous = true,
            cleanupResult = AccountDeletionError.RemoteDataCleanupFailed().left(),
        )
        env.viewModel.onEvent(DeleteUserAccountEvent.OnDeleteClick)

        runTest {
            env.viewModel.sideEffects.effectFlow.test {
                env.viewModel.onEvent(DeleteUserAccountEvent.OnConfirmAccepted)
                awaitItem() shouldBe DeleteUserAccountEffect.Notify(DeleteUserAccountNotice.CleanupFailed)
            }
        }
        env.viewModel.currentState.isDeleting shouldBe false
    }

    "successful deletion clears the session and keeps the overlay up" {
        val env = VmEnv(isAnonymous = true)
        env.viewModel.onEvent(DeleteUserAccountEvent.OnDeleteClick)

        env.viewModel.onEvent(DeleteUserAccountEvent.OnConfirmAccepted)

        // Navigation is driven by AppLaunchViewModel observing the cleared user pointer.
        env.session.currentUserId.flow.first() shouldBe null
        env.viewModel.currentState.isDeleting shouldBe true
        env.viewModel.currentState.showConfirmDialog shouldBe false
    }
})

private class VmEnv(
    isAnonymous: Boolean,
    reauthResult: Either<AuthError, Unit> = Unit.right(),
    cleanupResult: Either<AccountDeletionError, Unit> = Unit.right(),
    authDeleteResult: Either<AuthError, Unit> = Unit.right(),
) {
    val session = InMemorySessionPointers(
        currentUserId = UserId("uid-1"),
        currentFirebaseUid = "uid-1",
    )

    private val auth = object : AuthRemoteRepository {
        override fun currentUid(): String = "uid-1"
        override fun currentEmail(): String? = if (isAnonymous) null else "user@example.com"
        override fun isCurrentUserAnonymous(): Boolean = isAnonymous
        override suspend fun signInWithEmail(email: String, password: String) = error("unused")
        override suspend fun createUserWithEmail(email: String, password: String) = error("unused")
        override suspend fun signInAnonymously() = error("unused")
        override suspend fun signOut() = error("unused")
        override suspend fun reauthenticateWithEmail(email: String, password: String) = reauthResult
        override suspend fun deleteCurrentUser() = authDeleteResult
    }

    private val useCase = DeleteUserAccountUseCase(
        sessionShutdownGate = object : SessionShutdownGate {
            override suspend fun shutdown() = Unit
        },
        authRemoteRepository = auth,
        userAccountDeletionRepository = object : UserAccountDeletionRepository {
            override suspend fun deleteRemoteUserData(uid: String, email: String?) = cleanupResult
        },
        localDataResetRepository = object : LocalDataResetRepository {
            override suspend fun clearAll() = Unit
        },
        remoteDataResetRepository = object : RemoteDataResetRepository {
            override suspend fun clearAll() = Unit
        },
        session = session,
    )

    val viewModel = DeleteUserAccountViewModel(
        deleteUserAccount = useCase,
        authRemoteRepository = auth,
    )
}
