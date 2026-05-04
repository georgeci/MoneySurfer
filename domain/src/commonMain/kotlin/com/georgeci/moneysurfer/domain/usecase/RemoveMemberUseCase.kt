package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Owner removes another member: status → REMOVED.
 *
 * Refuses to remove the last OWNER ([InviteError.OwnerCannotLeave]) — workspace must always
 * have at least one owner. Transferring ownership is a separate (post-v1) flow.
 */
@Single
class RemoveMemberUseCase(
    private val memberRepository: WorkspaceMemberRepository,
    private val session: SessionPointers,
) {
    private val log = Logger.withTag(TAG)

    data class Params(
        val workspaceId: WorkspaceId,
        val targetUserId: UserId,
    )

    suspend operator fun invoke(params: Params): Either<InviteError, Unit> = either {
        val callerId = session.currentUserId.flow.first()
            ?: raise(InviteError.NoCurrentUser)

        val members = memberRepository.getByWorkspaceId(params.workspaceId).first()
        val callerMember = members.firstOrNull { it.userId == callerId }
        ensure(callerMember?.role == WorkspaceRole.OWNER) { InviteError.NotOwner }

        val target = members.firstOrNull { it.userId == params.targetUserId }
            ?: raise(InviteError.InviteNotFound)
        ensure(target.status == WorkspaceMemberStatus.ACTIVE) { InviteError.InviteNotPending }

        if (target.role == WorkspaceRole.OWNER) {
            val remainingOwners = members.count {
                it.role == WorkspaceRole.OWNER &&
                    it.status == WorkspaceMemberStatus.ACTIVE &&
                    it.userId != params.targetUserId
            }
            ensure(remainingOwners >= 1) { InviteError.OwnerCannotLeave }
        }

        Either.catch {
            memberRepository.markRemoved(
                userId = params.targetUserId,
                workspaceId = params.workspaceId,
                removedByUserId = callerId,
            )
        }
            .onLeft { log.e(it) { "[local] markRemoved failed uid=${params.targetUserId.value}" } }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        log.i { "[done] removed uid=${params.targetUserId.value} by=${callerId.value}" }
    }

    private companion object {
        const val TAG = "RemoveMember"
    }
}
