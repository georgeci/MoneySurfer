package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aUser
import com.georgeci.moneysurfer.domain.fixtures.aWorkspaceInvite
import com.georgeci.moneysurfer.domain.fixtures.userId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.fixtures.workspaceInviteId
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.currentTimeMillis
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class AcceptInviteUseCaseTest : StringSpec({

    "rejects when no current user" {
        val env = AcceptInviteEnv(currentUserId = null)
        val result = env.useCase(workspaceInviteId())
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.NoCurrentUser
    }

    "rejects when invite not found" {
        val env = AcceptInviteEnv()
        val result = env.useCase(workspaceInviteId("missing"))
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.InviteNotFound
    }

    "rejects when invite already accepted" {
        val env = AcceptInviteEnv()
        env.invites.seed(aWorkspaceInvite(id = INV_ID, status = InviteStatus.ACCEPTED))
        val result = env.useCase(INV_ID)
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.InviteNotPending
    }

    "rejects when invite is expired" {
        val env = AcceptInviteEnv()
        val past = currentTimeMillis() - 1_000L
        env.invites.seed(
            aWorkspaceInvite(
                id = INV_ID,
                email = "invitee@example.com",
                createdAt = past - 100,
                expiresAt = past,
            ),
        )
        val result = env.useCase(INV_ID)
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.InviteExpired
    }

    "rejects when invite email does not match the current user" {
        val env = AcceptInviteEnv()
        env.invites.seed(aWorkspaceInvite(id = INV_ID, email = "someone@else.com"))
        val result = env.useCase(INV_ID)
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.InviteNotFound
    }

    "happy path: creates ACTIVE member with invite role + marks invite ACCEPTED" {
        val env = AcceptInviteEnv()
        env.invites.seed(
            aWorkspaceInvite(
                id = INV_ID,
                workspaceId = WS,
                email = "invitee@example.com",
                role = WorkspaceRole.EDITOR,
                invitedByUserId = userId("owner-uid"),
            ),
        )

        val result = env.useCase(INV_ID)
        result.shouldBeInstanceOf<Either.Right<Unit>>()

        val member = env.members.rows.single()
        member.userId shouldBe INVITEE_ID
        member.workspaceId shouldBe WS
        member.role shouldBe WorkspaceRole.EDITOR
        member.status shouldBe WorkspaceMemberStatus.ACTIVE
        member.email shouldBe "invitee@example.com"
        member.displayName shouldBe "Invitee Name"
        member.addedByUserId shouldBe userId("owner-uid")

        env.invites.rows.single().status shouldBe InviteStatus.ACCEPTED
    }

    "case-insensitive email match" {
        val env = AcceptInviteEnv(callerEmail = "Invitee@Example.COM")
        env.invites.seed(aWorkspaceInvite(id = INV_ID, email = "invitee@example.com"))
        env.useCase(INV_ID).shouldBeInstanceOf<Either.Right<Unit>>()
    }

    "matches by targetUserId when email differs" {
        val env = AcceptInviteEnv(callerEmail = "renamed@example.com")
        env.invites.seed(
            aWorkspaceInvite(
                id = INV_ID,
                email = "old@example.com",
                targetUserId = INVITEE_ID,
            ),
        )
        env.useCase(INV_ID).shouldBeInstanceOf<Either.Right<Unit>>()
    }
})

private val INV_ID = workspaceInviteId("inv-1")
private val WS = workspaceId("ws-1")
private val INVITEE_ID = userId("invitee-uid")

private class AcceptInviteEnv(
    currentUserId: com.georgeci.moneysurfer.domain.primitives.UserId? = INVITEE_ID,
    callerEmail: String? = "invitee@example.com",
    val members: FakeMemberRepository = FakeMemberRepository(),
    val invites: FakeInviteRepository = FakeInviteRepository(),
    val users: FakeUserRepository = FakeUserRepository(),
    val userRemote: AcceptInviteNoopUserRemoteRepository = AcceptInviteNoopUserRemoteRepository(),
    val syncer: NoopWorkspaceSyncer = NoopWorkspaceSyncer(),
) {
    init {
        if (currentUserId != null) {
            users.seed(
                aUser(
                    id = currentUserId,
                    displayName = "Invitee Name",
                    email = callerEmail,
                ),
            )
        }
    }

    // currentFirebaseUid defaults to null → use case takes the local-only branch and
    // the remote/sync stubs are never touched by the existing tests. They're wired up
    // so the constructor compiles; future tests can override the env to exercise the
    // remote follow-ups.
    val session = InMemorySessionPointers(currentUserId = currentUserId)
    val useCase = AcceptInviteUseCase(
        inviteRepository = invites,
        memberRepository = members,
        userRepository = users,
        userRemoteRepository = userRemote,
        workspaceSyncer = syncer,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(),
    )
}

private class AcceptInviteNoopUserRemoteRepository : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = null
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error("not used")
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = error("not used")
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private class NoopWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll() = Unit
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
