package com.georgeci.moneysurfer.feature.login

import app.cash.turbine.test
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.RecordingSyncedSettingsSession
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.usecase.AbandonAuthSessionUseCase
import com.georgeci.moneysurfer.domain.usecase.AnonymousLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.LoginUseCase
import com.georgeci.moneysurfer.domain.usecase.PostAuthBootstrapUseCase
import com.georgeci.moneysurfer.domain.usecase.SignupUseCase
import com.georgeci.moneysurfer.domain.usecase.WipeDemoDataUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
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

    "sign-up against a taken address shows a dialog and switches to sign-in" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnToggleModeClick)
        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("secret1"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.dialogError shouldBe SignInError.EmailAlreadyInUse
        viewModel.currentState.emailError shouldBe null
        viewModel.currentState.mode shouldBe AuthMode.SignIn
        viewModel.currentState.isLoading shouldBe false
    }

    "a rules rejection surfaces as PermissionDenied in a dialog" {
        val viewModel = newViewModel(signupFailure = AuthError.Type.PermissionDenied)

        viewModel.submitSignUp()

        viewModel.currentState.dialogError shouldBe SignInError.PermissionDenied
        viewModel.currentState.formError shouldBe null
        viewModel.currentState.mode shouldBe AuthMode.SignUp
    }

    "a provider-rejected address keeps its specific copy in the dialog" {
        val viewModel = newViewModel(signupFailure = AuthError.Type.InvalidEmail)

        viewModel.submitSignUp()

        viewModel.currentState.dialogError shouldBe SignInError.EmailInvalid
    }

    // RequiresRecentLogin only ever comes out of the account-deletion flow; if it somehow reaches
    // sign-in there is no re-auth prompt here to act on it, so it must degrade to the generic copy.
    "RequiresRecentLogin degrades to the generic failure" {
        val viewModel = newViewModel(signupFailure = AuthError.Type.RequiresRecentLogin)

        viewModel.submitSignUp()

        viewModel.currentState.dialogError shouldBe SignInError.Unknown
    }

    "dismissing an auth error clears the dialog" {
        val viewModel = newViewModel()

        viewModel.submitSignUp()
        viewModel.currentState.dialogError shouldBe SignInError.EmailAlreadyInUse

        viewModel.onEvent(SignInEvent.OnErrorDismiss)

        viewModel.currentState.dialogError shouldBe null
    }

    "editing a field clears the pending error" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnSubmitClick)
        viewModel.currentState.error shouldBe SignInError.EmailRequired

        viewModel.onEvent(SignInEvent.OnEmailChanged("s"))

        viewModel.currentState.error shouldBe null
    }

    "toggling swaps the mode and drops the error the other mode produced" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnSubmitClick)
        viewModel.currentState.error shouldBe SignInError.EmailRequired

        viewModel.onEvent(SignInEvent.OnToggleModeClick)
        viewModel.currentState.mode shouldBe AuthMode.SignUp
        viewModel.currentState.error shouldBe null

        viewModel.onEvent(SignInEvent.OnToggleModeClick)
        viewModel.currentState.mode shouldBe AuthMode.SignIn
    }

    "sign-in accepts a short password — only sign-up enforces the provider's floor" {
        val viewModel = newViewModel(signInResult = SignInOutcome.FirstTimeUser)

        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("x"))

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(SignInEvent.OnSubmitClick)
            awaitItem().shouldBeInstanceOf<SignInEffect.NavigateToWorkspaceSelector>()
        }
    }

    "a whitespace-only email is reported as missing, not as malformed" {
        val viewModel = newViewModel()

        viewModel.onEvent(SignInEvent.OnEmailChanged("   "))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("secret1"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)

        viewModel.currentState.error shouldBe SignInError.EmailRequired
    }

    "a first-time sign-in sends the selector on with nothing to warn about" {
        val viewModel = newViewModel(signInResult = SignInOutcome.FirstTimeUser)

        viewModel.sideEffects.effectFlow.test {
            viewModel.submitSignIn()
            awaitItem() shouldBe SignInEffect.NavigateToWorkspaceSelector(cloudDataUnavailable = false)
        }
        viewModel.currentState.isLoading shouldBe false
        viewModel.currentState.error shouldBe null
    }

    "an existing user whose workspace hydrated is a plain success too" {
        val viewModel = newViewModel(signInResult = SignInOutcome.ExistingUserHydrated)

        viewModel.sideEffects.effectFlow.test {
            viewModel.submitSignIn()
            awaitItem() shouldBe SignInEffect.NavigateToWorkspaceSelector(cloudDataUnavailable = false)
        }
    }

    "a sign-in whose cloud workspaces never reached the device says so on the way out" {
        val viewModel = newViewModel(signInResult = SignInOutcome.CloudDataUnavailable)

        // Auth itself succeeded, so the screen still navigates — but the selector has to render
        // "your data isn't here" rather than an empty list reading as "you have no workspaces"
        // (issue #342).
        viewModel.sideEffects.effectFlow.test {
            viewModel.submitSignIn()
            awaitItem() shouldBe SignInEffect.NavigateToWorkspaceSelector(cloudDataUnavailable = true)
        }
    }

    "a failed sign-in releases the spinner so the form can be retried" {
        val viewModel = newViewModel(signInResult = SignInOutcome.Failure(AuthError.Type.InvalidCredentials))

        viewModel.submitSignIn()

        viewModel.currentState.isLoading shouldBe false
        viewModel.currentState.dialogError shouldBe SignInError.InvalidCredentials
        // A form-level failure belongs in the dialog, not under a field.
        viewModel.currentState.emailError shouldBe null
        viewModel.currentState.passwordError shouldBe null
    }

    "a second submit while one is in flight is ignored" {
        val auth = GatedAuthRemoteRepository()
        val viewModel = newViewModel(auth = auth)

        viewModel.onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
        viewModel.onEvent(SignInEvent.OnPasswordChanged("secret1"))
        viewModel.onEvent(SignInEvent.OnSubmitClick)
        viewModel.onEvent(SignInEvent.OnSubmitClick)
        viewModel.onEvent(SignInEvent.OnSubmitClick)
        auth.release()

        auth.signInCalls shouldBe 1
    }

    "the anonymous entry point runs its own auth and lands on the selector" {
        val viewModel = newViewModel(signInResult = SignInOutcome.FirstTimeUser)

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(SignInEvent.OnAnonymousLoginClick)
            awaitItem() shouldBe SignInEffect.NavigateToWorkspaceSelector(cloudDataUnavailable = false)
        }
    }

    "the demo entry point signs in locally and lands on the selector" {
        val viewModel = newViewModel()

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(SignInEvent.OnLoginClick)
            awaitItem() shouldBe SignInEffect.NavigateToWorkspaceSelector(cloudDataUnavailable = false)
        }
    }

    "the offline build renders as demo-only" {
        val viewModel = newViewModel(hostCapabilities = FakeHostCapabilities.offline())

        viewModel.currentState.demoOnly shouldBe true
        viewModel.currentState.emailPasswordEnabled shouldBe false
        viewModel.currentState.anonymousEnabled shouldBe false
    }

    "a build offering every entry point is not demo-only" {
        newViewModel().currentState.demoOnly shouldBe false
    }

    "Submit stays live while loading is off — a dead button explains nothing" {
        newViewModel().currentState.canSubmit shouldBe true
    }
})

/** Fills in valid credentials in sign-up mode and submits, so only the provider failure varies. */
private fun SignInViewModel.submitSignUp() {
    onEvent(SignInEvent.OnToggleModeClick)
    onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
    onEvent(SignInEvent.OnPasswordChanged("secret1"))
    onEvent(SignInEvent.OnSubmitClick)
}

/** The sign-in counterpart: valid credentials, so only the provider outcome varies. */
private fun SignInViewModel.submitSignIn() {
    onEvent(SignInEvent.OnEmailChanged("surfer@example.com"))
    onEvent(SignInEvent.OnPasswordChanged("secret1"))
    onEvent(SignInEvent.OnSubmitClick)
}

/**
 * What the provider plus the post-auth bootstrap add up to for one sign-in. The three success
 * shapes are what `PostAuthBootstrapUseCase` can return, and the view model maps each onto the
 * `cloudDataUnavailable` flag it hands the selector.
 */
private sealed interface SignInOutcome {
    /** No remote `users/{uid}` document yet — the bootstrap creates one. */
    data object FirstTimeUser : SignInOutcome

    /** A returning user whose default workspace made it into the local database. */
    data object ExistingUserHydrated : SignInOutcome

    /** A returning user whose workspaces the pull never brought down. */
    data object CloudDataUnavailable : SignInOutcome

    data class Failure(val type: AuthError.Type) : SignInOutcome
}

private const val TEST_UID = "uid-1"
private val HYDRATED_WORKSPACE_ID = WorkspaceId("ws-1")

@Suppress("LongParameterList")
private fun newViewModel(
    signupFailure: AuthError.Type = AuthError.Type.EmailAlreadyInUse,
    signInResult: SignInOutcome = SignInOutcome.FirstTimeUser,
    auth: AuthRemoteRepository = FakeAuthRemoteRepository(signupFailure, signInResult),
    hostCapabilities: FakeHostCapabilities = FakeHostCapabilities(),
): SignInViewModel {
    val remoteUser = when (signInResult) {
        SignInOutcome.ExistingUserHydrated -> aRemoteUser(listOf(HYDRATED_WORKSPACE_ID))
        SignInOutcome.CloudDataUnavailable -> aRemoteUser(listOf(WorkspaceId("never-pulled")))
        else -> null
    }
    val localWorkspaces = when (signInResult) {
        SignInOutcome.ExistingUserHydrated ->
            listOf(aWorkspace(id = HYDRATED_WORKSPACE_ID, ownerId = UserId(TEST_UID)))
        else -> emptyList()
    }
    val session = InMemorySessionPointers()
    val authLocal = AuthLocalRepository(FakeUserRepository(), session)
    val wipeDemo = WipeDemoDataUseCase(NoOpLocalDataResetRepository, session, session)
    val postAuthBootstrap = PostAuthBootstrapUseCase(
        userRemoteRepository = FakeUserRemoteRepository(remoteUser),
        workspaceRepository = FakeSignInWorkspaceRepository(localWorkspaces),
        workspaceSyncer = NoOpWorkspaceSyncer,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
        syncedSettingsSession = RecordingSyncedSettingsSession(),
    )
    val abandon = AbandonAuthSessionUseCase(session, auth)
    return SignInViewModel(
        login = LoginUseCase(auth, authLocal, session, wipeDemo, postAuthBootstrap, abandon),
        signup = SignupUseCase(auth, authLocal, session, wipeDemo, postAuthBootstrap, abandon),
        anonymousLogin = AnonymousLoginUseCase(
            auth,
            authLocal,
            session,
            wipeDemo,
            postAuthBootstrap,
            abandon,
        ),
        demoLogin = DemoLoginUseCase(authLocal, session, RecordingSyncedSettingsSession()),
        hostCapabilities = hostCapabilities,
    )
}

private fun aRemoteUser(workspaceIds: List<WorkspaceId>) = User(
    id = UserId(TEST_UID),
    displayName = null,
    email = "surfer@example.com",
    isAnon = false,
    workspaceIds = workspaceIds,
    defaultWorkspaceId = workspaceIds.firstOrNull(),
)

private const val UNUSED = "auth collaborator not exercised by this test"

private class FakeUserRepository : UserRepository {
    private val rows = mutableMapOf<UserId, User>()

    override fun getAll(): Flow<List<User>> = emptyFlow()
    override suspend fun getById(id: UserId): User? = rows[id]
    override suspend fun insert(user: User) {
        rows[user.id] = user
    }

    override suspend fun update(user: User) {
        rows[user.id] = user
    }

    override suspend fun upsert(user: User) {
        rows[user.id] = user
    }

    override suspend fun delete(id: UserId) {
        rows -= id
    }
}

private object NoOpLocalDataResetRepository : LocalDataResetRepository {
    override suspend fun clearAll() = Unit
}

/** [remoteUser] is the `users/{uid}` document — `null` stands for a first-time entry. */
private class FakeUserRemoteRepository(private val remoteUser: User?) : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = remoteUser
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = Unit
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun findByEmail(email: String): UserId? = error(UNUSED)
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
}

/** Stands in for Room after the pull: whatever is here counts as hydrated. */
private class FakeSignInWorkspaceRepository(
    private val hydrated: List<Workspace>,
) : WorkspaceRepository {
    override fun getAll(): Flow<List<Workspace>> = emptyFlow()
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = emptyFlow()
    override suspend fun getById(id: WorkspaceId): Workspace? = hydrated.firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) = error(UNUSED)
    override suspend fun update(workspace: Workspace) = error(UNUSED)
    override suspend fun delete(id: WorkspaceId) = error(UNUSED)
}

private object NoOpWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll(): Boolean = true
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}

private class FakeAuthRemoteRepository(
    private val signupFailure: AuthError.Type,
    private val signInResult: SignInOutcome,
) : AuthRemoteRepository {
    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false

    override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, String> =
        when (signInResult) {
            is SignInOutcome.Failure -> AuthError(signInResult.type).left()
            else -> TEST_UID.right()
        }

    // Whichever failure the case under test wants back from the provider, so the
    // AuthError -> SignInError mapping is observable.
    override suspend fun createUserWithEmail(email: String, password: String) =
        AuthError(signupFailure).left()

    override suspend fun signInAnonymously(): Either<AuthError, String> = TEST_UID.right()
    override suspend fun signOut(): Either<AuthError, Unit> = Unit.right()
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}

/** Signs in only once [release] is called, so a test can land taps while the screen is loading. */
private class GatedAuthRemoteRepository : AuthRemoteRepository {
    private val gate = CompletableDeferred<Unit>()

    var signInCalls = 0
        private set

    fun release() = gate.complete(Unit)

    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false

    override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, String> {
        signInCalls++
        gate.await()
        return TEST_UID.right()
    }

    override suspend fun createUserWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun signInAnonymously() = error(UNUSED)
    override suspend fun signOut(): Either<AuthError, Unit> = Unit.right()
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}
