package com.georgeci.moneysurfer.feature.login

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.usecase.AnonymousLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.LoginUseCase
import com.georgeci.moneysurfer.domain.usecase.PostAuthBootstrapUseCase
import com.georgeci.moneysurfer.domain.usecase.SignupUseCase
import com.georgeci.moneysurfer.domain.usecase.WipeDemoDataUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "OnTermsClick emits a NavigateToLegal side effect" {
        val viewModel = newViewModel()

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(SignInEvent.OnTermsClick)
            awaitItem() shouldBe SignInEffect.NavigateToLegal
        }
    }
})

private fun newViewModel(): SignInViewModel {
    val session = InMemorySessionPointers()
    val authLocal = AuthLocalRepository(StubUserRepository, session)
    val wipeDemo = WipeDemoDataUseCase(StubLocalDataResetRepository, session)
    val postAuthBootstrap = PostAuthBootstrapUseCase(
        userRemoteRepository = StubUserRemoteRepository,
        workspaceSyncer = StubWorkspaceSyncer,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    )
    return SignInViewModel(
        login = LoginUseCase(StubAuthRemoteRepository, authLocal, session, wipeDemo, postAuthBootstrap),
        signup = SignupUseCase(StubAuthRemoteRepository, authLocal, session, wipeDemo, postAuthBootstrap),
        anonymousLogin = AnonymousLoginUseCase(
            StubAuthRemoteRepository,
            authLocal,
            session,
            wipeDemo,
            postAuthBootstrap,
        ),
        demoLogin = DemoLoginUseCase(authLocal, session),
        config = SignInFeatureConfig(),
    )
}

private const val UNUSED = "auth collaborator not exercised by this test"

private object StubUserRepository : UserRepository {
    override fun getAll(): Flow<List<User>> = emptyFlow()
    override suspend fun getById(id: UserId): User? = error(UNUSED)
    override suspend fun insert(user: User) = error(UNUSED)
    override suspend fun update(user: User) = error(UNUSED)
    override suspend fun upsert(user: User) = error(UNUSED)
    override suspend fun delete(id: UserId) = error(UNUSED)
}

private object StubLocalDataResetRepository : LocalDataResetRepository {
    override suspend fun clearAll() = error(UNUSED)
}

private object StubUserRemoteRepository : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = error(UNUSED)
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error(UNUSED)
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun findByEmail(email: String): UserId? = error(UNUSED)
    override suspend fun upsertEmailMapping(email: String, uid: String) = error(UNUSED)
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
}

private object StubWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll() = error(UNUSED)
    override suspend fun syncAll() = error(UNUSED)
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = error(UNUSED)
}

private object StubAuthRemoteRepository : AuthRemoteRepository {
    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun createUserWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun signInAnonymously() = error(UNUSED)
    override suspend fun signOut() = error(UNUSED)
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}
