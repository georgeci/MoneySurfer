package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aWorkspaceMember
import com.georgeci.moneysurfer.domain.fixtures.userId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RemoveMemberUseCaseTest : StringSpec({

    "rejects when caller is not OWNER" {
        val env = roster(callerRole = WorkspaceRole.EDITOR)
        val result = env.useCase(RemoveMemberUseCase.Params(WS, EDITOR_ID))
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.NotOwner
    }

    "rejects when target is not a member" {
        val env = roster()
        val result = env.useCase(RemoveMemberUseCase.Params(WS, userId("ghost")))
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.InviteNotFound
    }

    "rejects when removing the last OWNER" {
        val env = roster(secondOwner = false)
        val result = env.useCase(RemoveMemberUseCase.Params(WS, OWNER_ID))
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.OwnerCannotLeave
    }

    "happy path: marks target REMOVED" {
        val env = roster()
        val result = env.useCase(RemoveMemberUseCase.Params(WS, EDITOR_ID))
        result.shouldBeInstanceOf<Either.Right<Unit>>()
        env.members.rows.first { it.userId == EDITOR_ID }.status shouldBe WorkspaceMemberStatus.REMOVED
    }
})

class LeaveWorkspaceUseCaseTest : StringSpec({

    "rejects when no current user" {
        val env = roster(callerId = null)
        val result = env.useCase(WS)
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.NoCurrentUser
    }

    "rejects when last OWNER tries to leave" {
        val env = roster(callerRole = WorkspaceRole.OWNER, secondOwner = false)
        val result = env.useCase(WS)
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.OwnerCannotLeave
    }

    "happy path: editor can leave" {
        val env = roster(callerRole = WorkspaceRole.EDITOR)
        val result = env.useCase(WS)
        result.shouldBeInstanceOf<Either.Right<Unit>>()
        env.members.rows.first { it.userId == EDITOR_ID }.status shouldBe WorkspaceMemberStatus.LEFT
    }

    "owner can leave when another owner remains" {
        val env = roster(callerRole = WorkspaceRole.OWNER, secondOwner = true)
        val result = env.useCase(WS)
        result.shouldBeInstanceOf<Either.Right<Unit>>()
    }
})

class ChangeMemberRoleUseCaseTest : StringSpec({

    "rejects demoting last OWNER" {
        val env = roster(secondOwner = false)
        val result = env.useCase(
            ChangeMemberRoleUseCase.Params(WS, OWNER_ID, WorkspaceRole.EDITOR),
        )
        result.shouldBeInstanceOf<Either.Left<InviteError>>().value shouldBe InviteError.CannotDemoteLastOwner
    }

    "no-op when role unchanged" {
        val env = roster()
        val before = env.members.rows.toList()
        val result = env.useCase(
            ChangeMemberRoleUseCase.Params(WS, EDITOR_ID, WorkspaceRole.EDITOR),
        )
        result.shouldBeInstanceOf<Either.Right<Unit>>()
        env.members.rows shouldBe before
    }

    "promotes editor to owner" {
        val env = roster()
        val result = env.useCase(
            ChangeMemberRoleUseCase.Params(WS, EDITOR_ID, WorkspaceRole.OWNER),
        )
        result.shouldBeInstanceOf<Either.Right<Unit>>()
        env.members.rows.first { it.userId == EDITOR_ID }.role shouldBe WorkspaceRole.OWNER
    }
})

private val WS = workspaceId("ws-1")
private val OWNER_ID = userId("owner-uid")
private val SECOND_OWNER_ID = userId("owner-2-uid")
private val EDITOR_ID = userId("editor-uid")

private class RosterEnv(
    callerId: com.georgeci.moneysurfer.domain.primitives.UserId?,
    val members: FakeMemberRepository,
) {
    val session = InMemorySessionPointers(currentUserId = callerId)
    val removeUseCase = RemoveMemberUseCase(members, session)
    val leaveUseCase = LeaveWorkspaceUseCase(members, session)
    val changeRoleUseCase = ChangeMemberRoleUseCase(members, session)

    suspend operator fun invoke(params: RemoveMemberUseCase.Params) = removeUseCase(params)
    suspend operator fun invoke(workspaceId: com.georgeci.moneysurfer.domain.primitives.WorkspaceId) = leaveUseCase(
        workspaceId,
    )
    suspend operator fun invoke(params: ChangeMemberRoleUseCase.Params) = changeRoleUseCase(params)

    val useCase: RosterEnv get() = this
}

private fun roster(
    callerRole: WorkspaceRole = WorkspaceRole.OWNER,
    secondOwner: Boolean = false,
    callerId: com.georgeci.moneysurfer.domain.primitives.UserId? = if (callerRole == WorkspaceRole.OWNER) OWNER_ID else EDITOR_ID,
): RosterEnv {
    val members = FakeMemberRepository()
    members.seed(
        aWorkspaceMember(
            userId = OWNER_ID,
            workspaceId = WS,
            role = WorkspaceRole.OWNER,
            status = WorkspaceMemberStatus.ACTIVE,
        ),
        aWorkspaceMember(
            userId = EDITOR_ID,
            workspaceId = WS,
            role = WorkspaceRole.EDITOR,
            status = WorkspaceMemberStatus.ACTIVE,
        ),
    )
    if (secondOwner) {
        members.seed(
            aWorkspaceMember(
                userId = SECOND_OWNER_ID,
                workspaceId = WS,
                role = WorkspaceRole.OWNER,
                status = WorkspaceMemberStatus.ACTIVE,
            ),
        )
    }
    return RosterEnv(callerId = callerId, members = members)
}
