package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import org.koin.core.annotation.Single

/**
 * Owner changes a member's role. Refuses to demote the last OWNER.
 */
@Single
class ChangeMemberRoleUseCase(
    private val memberRepository: WorkspaceMemberRepository,
    private val session: SessionPointers,
) {
    private val log = Logger.withTag(TAG)

    data class Params(
        val workspaceId: WorkspaceId,
        val targetUserId: UserId,
        val newRole: WorkspaceRole,
    )

    suspend operator fun invoke(params: Params): Either<InviteError, Unit> = either {
        val (callerId, members, target) = resolveOwnerActionTarget(
            session = session,
            memberRepository = memberRepository,
            workspaceId = params.workspaceId,
            targetUserId = params.targetUserId,
        )

        if (target.role == WorkspaceRole.OWNER && params.newRole != WorkspaceRole.OWNER) {
            val remainingOwners = members.count {
                it.role == WorkspaceRole.OWNER &&
                    it.status == WorkspaceMemberStatus.ACTIVE &&
                    it.userId != params.targetUserId
            }
            ensure(remainingOwners >= 1) { InviteError.CannotDemoteLastOwner }
        }

        // No-op fast path: avoid writing if nothing actually changes.
        if (target.role == params.newRole) return@either

        Either.catch { memberRepository.update(target.copy(role = params.newRole)) }
            .onLeft { log.e(it) { "[local] update failed uid=${params.targetUserId.value.redactUid()}" } }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        log.i {
            "[done] uid=${params.targetUserId.value.redactUid()} ${target.role} -> ${params.newRole} " +
                "by=${callerId.value.redactUid()}"
        }
    }

    private companion object {
        const val TAG = "ChangeMemberRole"
    }
}
