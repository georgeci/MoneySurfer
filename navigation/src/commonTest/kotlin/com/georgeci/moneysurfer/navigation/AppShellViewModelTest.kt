package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant

private val USER = UserId("u-1")
private val WORKSPACE = WorkspaceId("ws-1")

private fun user(displayName: String?) = User(
    id = USER,
    displayName = displayName,
    email = null,
    isAnon = false,
)

private fun workspace(name: String) = Workspace(
    id = WORKSPACE,
    name = name,
    description = "",
    baseCurrency = CurrencyCode("USD"),
    ownerId = USER,
    createdAt = Instant.fromEpochMilliseconds(0),
)

private class FakeUserRepository(users: List<User>) : UserRepository {
    private val state = MutableStateFlow(users)
    override fun getAll(): Flow<List<User>> = state
    override suspend fun getById(id: UserId): User? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(user: User) = Unit
    override suspend fun update(user: User) = Unit
    override suspend fun upsert(user: User) = Unit
    override suspend fun delete(id: UserId) = Unit
}

private class FakeWorkspaceRepository(workspaces: List<Workspace>) : WorkspaceRepository {
    private val state = MutableStateFlow(workspaces)
    override fun getAll(): Flow<List<Workspace>> = state
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = state
    override suspend fun getById(id: WorkspaceId): Workspace? =
        state.value.firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) = Unit
    override suspend fun update(workspace: Workspace) = Unit
    override suspend fun delete(id: WorkspaceId) = Unit
}

private class StubAuthRemoteRepository(private val email: String?) : AuthRemoteRepository {
    override fun currentUid(): String? = USER.value
    override fun currentEmail(): String? = email
    override fun isCurrentUserAnonymous(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = error("not used")
    override suspend fun createUserWithEmail(email: String, password: String) = error("not used")
    override suspend fun signInAnonymously() = error("not used")
    override suspend fun signOut() = error("not used")
    override suspend fun reauthenticateWithEmail(email: String, password: String) =
        error("not used")
    override suspend fun deleteCurrentUser() = error("not used")
}

private fun viewModel(
    users: List<User> = listOf(user("Ada")),
    workspaces: List<Workspace> = listOf(workspace("Household")),
    providerEmail: String? = null,
    session: InMemorySessionPointers = InMemorySessionPointers(
        currentUserId = USER,
        currentWorkspaceId = WORKSPACE,
    ),
) = AppShellViewModel(
    session = session,
    userRepository = FakeUserRepository(users),
    workspaceRepository = FakeWorkspaceRepository(workspaces),
    authRemoteRepository = StubAuthRemoteRepository(providerEmail),
)

/**
 * The drawer footer's two lines, assembled from four independent sources. `ShellIdentityTest`
 * covers the resolution rules; this covers the wiring — that the flow starts blank rather than
 * blocking the drawer on four reads, and that it re-emits when any of the four changes.
 */
class AppShellViewModelTest : StringSpec({

    beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterTest { Dispatchers.resetMain() }

    "the footer starts blank, so the drawer renders before the session resolves" {
        viewModel().identity.value shouldBe AppShellIdentity()
    }

    "a resolved session names the user and the workspace" {
        runTest {
            val seen = viewModel().collectIdentity(this)

            seen.first() shouldBe AppShellIdentity()
            seen.last() shouldBe AppShellIdentity(userName = "Ada", workspaceName = "Household")
        }
    }

    // The local user row reliably carries only the display name; the email belongs to the auth
    // provider, which is where the settings screen reads it from too.
    "a user with no stored name falls back to the provider's email" {
        runTest {
            val viewModel = viewModel(
                users = listOf(user(null)),
                providerEmail = "ada@example.com",
            )

            viewModel.collectIdentity(this).last().userName shouldBe "ada@example.com"
        }
    }

    "switching workspace re-emits the footer" {
        runTest {
            val session = InMemorySessionPointers(currentUserId = USER, currentWorkspaceId = null)
            val seen = viewModel(session = session).collectIdentity(this)
            seen.last().workspaceName shouldBe null

            session.setCurrentWorkspace(WORKSPACE)

            seen.last().workspaceName shouldBe "Household"
        }
    }
})

/**
 * Every value the footer has taken so far. A live subscription rather than a one-shot read: the
 * flow is `WhileSubscribed`, so nothing upstream runs — and nothing re-emits — without one.
 */
private fun AppShellViewModel.collectIdentity(scope: TestScope): List<AppShellIdentity> {
    val seen = mutableListOf<AppShellIdentity>()
    scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
        identity.collect { seen += it }
    }
    return seen
}
