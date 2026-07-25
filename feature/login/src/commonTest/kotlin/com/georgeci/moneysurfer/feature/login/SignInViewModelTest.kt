package com.georgeci.moneysurfer.feature.login

import app.cash.turbine.test
import arrow.core.left
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
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

    "submitting with no email reports EmailRequired against the email field" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.error shouldBe SignInError.EmailRequired
        viewModel.currentState.emailError shouldBe SignInError.EmailRequired
        viewModel.currentState.passwordError shouldBe null
    }

    "submitting with a malformed email reports EmailInvalid" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example"))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("secret1"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.error shouldBe SignInError.EmailInvalid
    }

    "submitting a valid email with no password reports PasswordRequired" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.error shouldBe SignInError.PasswordRequired
        viewModel.currentState.passwordError shouldBe SignInError.PasswordRequired
    }

    "sign-up with a short password reports PasswordTooShort instead of silently doing nothing" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnToggleModeClick)
        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("12345"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.mode shouldBe AuthMode.SignUp
        viewModel.currentState.error shouldBe SignInError.PasswordTooShort
    }

    "sign-up against a taken address reports it on the email field and switches to sign-in" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnToggleModeClick)
        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("secret1"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.emailError shouldBe SignInError.EmailAlreadyInUse
        viewModel.currentState.mode shouldBe AuthMode.SignIn
        viewModel.currentState.isLoading shouldBe false
    }

    "a rules rejection surfaces as PermissionDenied on the form, not on a field" {
        val viewModel = newViewModel(signupFailure = AuthError.Type.PermissionDenied)

        viewModel.submitSignUp()

        viewModel.currentState.formError shouldBe SignInError.PermissionDenied
        viewModel.currentState.mode shouldBe AuthMode.SignUp
    }

    "a provider-rejected address lands on the email field rather than reading as a bad password" {
        val viewModel = newViewModel(signupFailure = AuthError.Type.InvalidEmail)

        viewModel.submitSignUp()

        viewModel.currentState.emailError shouldBe SignInError.EmailInvalid
    }

    // RequiresRecentLogin only ever comes out of the account-deletion flow; if it somehow reaches
    // sign-in there is no re-auth prompt here to act on it, so it must degrade to the generic copy.
    "RequiresRecentLogin degrades to the generic failure" {
        val viewModel = newViewModel(signupFailure = AuthError.Type.RequiresRecentLogin)

        viewModel.submitSignUp()

        viewModel.currentState.formError shouldBe SignInError.Unknown
    }

    "editing a field clears the pending error" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnSubmitClick)
        viewModel.currentState.error shouldBe SignInError.EmailRequired

        viewModel.onEvent(SignInEvent.OnEmailChanged("s"))

        viewModel.currentState.error shouldBe null
    }
})

/** Fills in valid credentials in sign-up mode and submits, so only the provider failure varies. */
private fun SignInViewModel.submitSignUp() {
    onEvent(SignInEvent.OnToggleModeClick)
    onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
    onEvent(SignInEvent.OnPasswordChanged("secret1"))
    onEvent(SignInEvent.OnSubmitClick)
}

private fun newViewModel(
    signupFailure: AuthError.Type = AuthError.Type.EmailAlreadyInUse,
): SignInViewModel {
    val auth = StubAuthRemoteRepository(signupFailure)
    val session = InMemorySessionPointers()
    val authLocal = AuthLocalRepository(StubUserRepository, session)
    val wipeDemo = WipeDemoDataUseCase(StubLocalDataResetRepository, session, session)
    val postAuthBootstrap = PostAuthBootstrapUseCase(
        userRemoteRepository = StubUserRemoteRepository,
        workspaceSyncer = StubWorkspaceSyncer,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    )
    return SignInViewModel(
        login = LoginUseCase(auth, authLocal, session, wipeDemo, postAuthBootstrap),
        signup = SignupUseCase(auth, authLocal, session, wipeDemo, postAuthBootstrap),
        anonymousLogin = AnonymousLoginUseCase(
            auth,
            authLocal,
            session,
            wipeDemo,
            postAuthBootstrap,
        ),
        demoLogin = DemoLoginUseCase(authLocal, session),
        hostCapabilities = FakeHostCapabilities(),
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

private class StubAuthRemoteRepository(
    private val signupFailure: AuthError.Type,
) : AuthRemoteRepository {
    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = error(UNUSED)

    // The one collaborator call the tests do exercise: whichever failure the case under test
    // wants back from the provider, so the AuthError -> SignInError mapping is observable.
    override suspend fun createUserWithEmail(email: String, password: String) =
        AuthError(signupFailure).left()

    override suspend fun signInAnonymously() = error(UNUSED)
    override suspend fun signOut() = error(UNUSED)
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}
