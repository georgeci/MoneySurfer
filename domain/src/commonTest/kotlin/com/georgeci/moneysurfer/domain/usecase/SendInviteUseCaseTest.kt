package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aWorkspaceInvite
import com.georgeci.moneysurfer.domain.fixtures.aWorkspaceMember
import com.georgeci.moneysurfer.domain.fixtures.userId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.Clock
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SendInviteUseCaseTest : StringSpec({

    "rejects when no current user" {
        val env = SendInviteEnv(currentUserId = null)
        val result = env.useCase(params())
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.NoCurrentUser
    }

    "rejects when caller is not OWNER" {
        val env = SendInviteEnv()
        env.members.seed(
            aWorkspaceMember(
                userId = OWNER_ID,
                workspaceId = WS,
                role = WorkspaceRole.EDITOR, // not owner
            ),
        )
        val result = env.useCase(params())
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.NotOwner
    }

    "rejects invalid email" {
        val env = ownerSendInviteEnv()
        val result = env.useCase(params(email = "not-an-email"))
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.InvalidEmail
    }

    "rejects when no registered user for email" {
        val env = ownerSendInviteEnv(emailToUid = emptyMap())
        val result = env.useCase(params())
        val left = result.shouldBeInstanceOf<Either.Left<InviteError>>().value
        left.shouldBeInstanceOf<InviteError.UserNotFound>().email shouldBe "invitee@example.com"
    }

    "surfaces remote lookup failure as RemoteSyncFailed" {
        val boom = RuntimeException("offline")
        val env = ownerSendInviteEnv(userRemote = ThrowingUserRemoteRepository(boom))
        val result = env.useCase(params())
        val left = result.shouldBeInstanceOf<Either.Left<InviteError>>().value
        left.shouldBeInstanceOf<InviteError.RemoteSyncFailed>()
    }

    "stamps targetUserId from userEmails mapping" {
        val env = ownerSendInviteEnv(emailToUid = mapOf("invitee@example.com" to "uid-invitee"))
        val result = env.useCase(params())
        result.shouldBeInstanceOf<Either.Right<WorkspaceInviteId>>()
        env.invites.rows.single().targetUserId?.value shouldBe "uid-invitee"
    }

    "rejects when email already an active member" {
        val env = ownerSendInviteEnv()
        env.members.seed(
            aWorkspaceMember(
                userId = userId("existing"),
                workspaceId = WS,
                role = WorkspaceRole.EDITOR,
                email = "invitee@example.com",
                status = WorkspaceMemberStatus.ACTIVE,
            ),
        )
        val result = env.useCase(params(email = "Invitee@Example.com"))
        val left = result.shouldBeInstanceOf<Either.Left<InviteError>>().value
        left.shouldBeInstanceOf<InviteError.AlreadyMember>()
        (left as InviteError.AlreadyMember).email shouldBe "invitee@example.com"
    }

    "ignores REMOVED ex-members when checking duplicates" {
        val env = ownerSendInviteEnv()
        env.members.seed(
            aWorkspaceMember(
                userId = userId("ex"),
                workspaceId = WS,
                role = WorkspaceRole.EDITOR,
                email = "invitee@example.com",
                status = WorkspaceMemberStatus.REMOVED,
            ),
        )
        val result = env.useCase(params())
        result.shouldBeInstanceOf<Either.Right<WorkspaceInviteId>>()
    }

    "rejects when a PENDING invite for the email already exists" {
        val env = ownerSendInviteEnv()
        env.invites.seed(
            aWorkspaceInvite(
                workspaceId = WS,
                email = "invitee@example.com",
                status = InviteStatus.PENDING,
            ),
        )
        val result = env.useCase(params())
        val left = result.shouldBeInstanceOf<Either.Left<InviteError>>().value
        left.shouldBeInstanceOf<InviteError.AlreadyInvited>()
    }

    "tolerates DECLINED/CANCELLED past invites for the email" {
        val env = ownerSendInviteEnv()
        env.invites.seed(
            aWorkspaceInvite(
                workspaceId = WS,
                email = "invitee@example.com",
                status = InviteStatus.DECLINED,
            ),
        )
        val result = env.useCase(params())
        result.shouldBeInstanceOf<Either.Right<WorkspaceInviteId>>()
    }

    "happy path: persists PENDING invite with TTL and lowercase email" {
        val env = ownerSendInviteEnv()
        val before = env.now()

        val result = env.useCase(params(email = "  Invitee@Example.com "))

        val newId = result.shouldBeInstanceOf<Either.Right<WorkspaceInviteId>>().value
        val invite = env.invites.rows.single()
        invite.id shouldBe newId
        invite.workspaceId shouldBe WS
        invite.email shouldBe "invitee@example.com"
        invite.role shouldBe WorkspaceRole.EDITOR
        invite.status shouldBe InviteStatus.PENDING
        invite.invitedByUserId shouldBe OWNER_ID
        // 14d TTL window — anchor on `before` to keep the assertion deterministic.
        (invite.expiresAt > before).let { it shouldBe true }
        ((invite.expiresAt - invite.createdAt) >= kotlin.time.Duration.parse("13d")) shouldBe true
    }
})

private val OWNER_ID = userId("owner-uid")
private val WS = workspaceId("ws-1")

private fun params(email: String = "invitee@example.com", role: WorkspaceRole = WorkspaceRole.EDITOR) =
    SendInviteUseCase.Params(workspaceId = WS, email = email, role = role)

private class SendInviteEnv(
    currentUserId: com.georgeci.moneysurfer.domain.primitives.UserId? = OWNER_ID,
    val members: FakeMemberRepository = FakeMemberRepository(),
    val invites: FakeInviteRepository = FakeInviteRepository(),
    val userRemote: com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository =
        NoopUserRemoteRepository(),
) {
    val session = InMemorySessionPointers(currentUserId = currentUserId)
    val getNow = GetCurrentTimeUseCase(Clock())
    fun now(): kotlin.time.Instant = getNow()
    val useCase = SendInviteUseCase(
        memberRepository = members,
        inviteRepository = invites,
        userRemoteRepository = userRemote,
        session = session,
        getCurrentTime = getNow,
    )
}

private class NoopUserRemoteRepository(
    private val emailToUid: Map<String, String> = emptyMap(),
) : com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository {
    override suspend fun fetch(uid: String) = null
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error("not used")
    override suspend fun addWorkspaceRef(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = error("not used")
    override suspend fun setDefaultWorkspace(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = error("not used")
    override suspend fun findByEmail(email: String) =
        emailToUid[email.lowercase()]?.let { com.georgeci.moneysurfer.domain.primitives.UserId(it) }
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = Unit
    override suspend fun removeInvitedWorkspaceRef(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = Unit
}

private class ThrowingUserRemoteRepository(
    private val cause: Throwable,
) : com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository {
    override suspend fun fetch(uid: String) = null
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error("not used")
    override suspend fun addWorkspaceRef(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = error("not used")
    override suspend fun setDefaultWorkspace(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = error("not used")
    override suspend fun findByEmail(email: String): com.georgeci.moneysurfer.domain.primitives.UserId? =
        throw cause
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = Unit
    override suspend fun removeInvitedWorkspaceRef(
        uid: String,
        workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId,
    ) = Unit
}

private fun ownerSendInviteEnv(
    emailToUid: Map<String, String> = mapOf("invitee@example.com" to "uid-invitee"),
    userRemote: com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository =
        NoopUserRemoteRepository(emailToUid),
): SendInviteEnv {
    val env = SendInviteEnv(userRemote = userRemote)
    env.members.seed(
        aWorkspaceMember(
            userId = OWNER_ID,
            workspaceId = WS,
            role = WorkspaceRole.OWNER,
            status = WorkspaceMemberStatus.ACTIVE,
        ),
    )
    return env
}
