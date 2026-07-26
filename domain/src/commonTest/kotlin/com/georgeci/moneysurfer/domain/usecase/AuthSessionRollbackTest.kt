package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Issue #342: `LoginUseCase` and friends pin `currentUserId` / `currentFirebaseUid` before the
 * post-auth bootstrap runs, because the pull reads those pointers to discover the user's
 * workspaces. When the bootstrap then fails the sign-in form shows an error but the app already
 * considers the user signed in — and `resolveStartRoute` sends the next cold start past sign-in
 * into a workspace selector with nothing in it and no way out.
 *
 * So the pointers must be rolled back on `Left`, and left alone on `Right`.
 */
class AuthSessionRollbackTest : StringSpec({

    "login rolls the session back and signs out when the bootstrap fails" {
        val env = AuthEnv(bootstrapFails = true)

        val result = env.login(email = "surfer@example.com", password = "secret1")

        result.shouldBeInstanceOf<Either.Left<AuthError>>()
        env.session.currentUserId.first() shouldBe null
        env.session.currentFirebaseUid.first() shouldBe null
        env.session.currentWorkspaceId.first() shouldBe null
        env.auth.signOutCount shouldBe 1
    }

    "login keeps the session when the bootstrap succeeds" {
        val env = AuthEnv(bootstrapFails = false)

        val result = env.login(email = "surfer@example.com", password = "secret1")

        result.shouldBeInstanceOf<Either.Right<PostAuthBootstrapUseCase.Result>>()
        env.session.currentUserId.first() shouldBe UserId(UID)
        env.session.currentFirebaseUid.first() shouldBe UID
        env.auth.signOutCount shouldBe 0
    }

    "signup rolls the session back when the bootstrap fails" {
        val env = AuthEnv(bootstrapFails = true)

        val result = env.signup(email = "surfer@example.com", password = "secret1")

        result.shouldBeInstanceOf<Either.Left<AuthError>>()
        env.session.currentUserId.first() shouldBe null
        env.session.currentFirebaseUid.first() shouldBe null
        env.auth.signOutCount shouldBe 1
    }

    "anonymous login rolls the session back but keeps the provider session" {
        // An anonymous account has no credential to sign back in with: `signInAnonymously()`
        // reuses `auth.currentUser` if there is one and mints a NEW uid otherwise. Signing out
        // over a transient bootstrap failure would orphan whatever that uid already owns on
        // Firestore, permanently.
        val env = AuthEnv(bootstrapFails = true)

        val result = env.anonymousLogin()

        result.shouldBeInstanceOf<Either.Left<AuthError>>()
        env.session.currentUserId.first() shouldBe null
        env.session.currentFirebaseUid.first() shouldBe null
        env.auth.signOutCount shouldBe 0
    }

    "a failing signOut still leaves the pointers cleared" {
        val env = AuthEnv(bootstrapFails = true, signOutThrows = true)

        env.login(email = "surfer@example.com", password = "secret1")
            .shouldBeInstanceOf<Either.Left<AuthError>>()

        env.session.currentUserId.first() shouldBe null
        env.session.currentFirebaseUid.first() shouldBe null
    }
})

private const val UID = "uid-342"
private const val UNUSED = "collaborator not exercised by this test"

private class AuthEnv(
    bootstrapFails: Boolean,
    signOutThrows: Boolean = false,
) {
    val session = InMemorySessionPointers()
    val auth = RecordingAuthRepo(signOutThrows = signOutThrows)

    private val authLocal = AuthLocalRepository(NoopUserRepository, session)
    private val wipeDemo = WipeDemoDataUseCase(NoopLocalDataReset, session, session)
    private val bootstrap = PostAuthBootstrapUseCase(
        userRemoteRepository = if (bootstrapFails) ThrowingUserRemoteRepo else EmptyUserRemoteRepo,
        workspaceRepository = NoWorkspaces,
        workspaceSyncer = NoopSyncer,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    )
    private val abandon = AbandonAuthSessionUseCase(session, auth)

    suspend fun login(email: String, password: String) =
        LoginUseCase(auth, authLocal, session, wipeDemo, bootstrap, abandon)(email, password)

    suspend fun signup(email: String, password: String) =
        SignupUseCase(auth, authLocal, session, wipeDemo, bootstrap, abandon)(email, password)

    suspend fun anonymousLogin() =
        AnonymousLoginUseCase(auth, authLocal, session, wipeDemo, bootstrap, abandon)()
}

private class RecordingAuthRepo(
    private val signOutThrows: Boolean,
) : AuthRemoteRepository {
    var signOutCount = 0

    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = UID.right()
    override suspend fun createUserWithEmail(email: String, password: String) = UID.right()
    override suspend fun signInAnonymously() = UID.right()
    override suspend fun signOut(): Either<AuthError, Unit> {
        signOutCount++
        if (signOutThrows) error("provider unreachable")
        return Unit.right()
    }
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}

private object NoopUserRepository : UserRepository {
    override fun getAll(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun getById(id: UserId): User? = null
    override suspend fun insert(user: User) = Unit
    override suspend fun update(user: User) = Unit
    override suspend fun upsert(user: User) = Unit
    override suspend fun delete(id: UserId) = Unit
}

private object NoopLocalDataReset : LocalDataResetRepository {
    override suspend fun clearAll() = Unit
}

private object NoWorkspaces : WorkspaceRepository {
    override fun getAll(): Flow<List<Workspace>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flowOf(emptyList())
    override suspend fun getById(id: WorkspaceId): Workspace? = null
    override suspend fun insert(workspace: Workspace) = error(UNUSED)
    override suspend fun update(workspace: Workspace) = error(UNUSED)
    override suspend fun delete(id: WorkspaceId) = error(UNUSED)
}

private object NoopSyncer : WorkspaceSyncer {
    override suspend fun pushAll(): Boolean = true
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}

/** Fails the bootstrap at its very first remote read. */
private object ThrowingUserRemoteRepo : StubUserRemoteRepo() {
    override suspend fun fetch(uid: String): User = error("PERMISSION_DENIED")
}

/** First-time entry: no `users/{uid}` document, so the bootstrap creates one and succeeds. */
private object EmptyUserRemoteRepo : StubUserRemoteRepo() {
    override suspend fun fetch(uid: String): User? = null
}

private abstract class StubUserRemoteRepo : UserRemoteRepository {
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = Unit
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun findByEmail(email: String): UserId? = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}
