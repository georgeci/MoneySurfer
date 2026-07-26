package com.georgeci.moneysurfer.domain.usecase

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import kotlinx.coroutines.flow.first

/**
 * Resolved context for an owner-initiated action on another member: the acting owner's id,
 * the full workspace roster, and the ACTIVE member being acted upon.
 */
internal data class OwnerActionTarget(
    val callerId: UserId,
    val members: List<WorkspaceMember>,
    val target: WorkspaceMember,
)

/**
 * Shared prologue for owner-only member actions ([ChangeMemberRoleUseCase], [RemoveMemberUseCase]):
 * loads the roster, asserts the caller is an OWNER, and resolves the ACTIVE target member.
 *
 * Raises on the enclosing [Raise] context:
 * - [InviteError.NoCurrentUser] — no signed-in user,
 * - [InviteError.NotOwner] — caller is not an OWNER of the workspace,
 * - [InviteError.InviteNotFound] — target is not a member,
 * - [InviteError.InviteNotPending] — target is not ACTIVE.
 */
internal suspend fun Raise<InviteError>.resolveOwnerActionTarget(
    session: SessionPointers,
    memberRepository: WorkspaceMemberRepository,
    workspaceId: WorkspaceId,
    targetUserId: UserId,
): OwnerActionTarget {
    val callerId = session.currentUserId.first()
        ?: raise(InviteError.NoCurrentUser)

    val members = memberRepository.getByWorkspaceId(workspaceId).first()
    val callerMember = members.firstOrNull { it.userId == callerId }
    ensure(callerMember?.role == WorkspaceRole.OWNER) { InviteError.NotOwner }

    val target = members.firstOrNull { it.userId == targetUserId }
        ?: raise(InviteError.InviteNotFound)
    ensure(target.status == WorkspaceMemberStatus.ACTIVE) { InviteError.InviteNotPending }

    return OwnerActionTarget(callerId, members, target)
}
