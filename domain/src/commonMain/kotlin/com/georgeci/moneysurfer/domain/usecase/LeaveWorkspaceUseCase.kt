package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Current user voluntarily leaves a workspace: status → LEFT.
 *
 * Refuses if caller is the last OWNER ([InviteError.OwnerCannotLeave]) — `TransferOwnership`
 * is post-v1 (md/members.md decision 5). Until that lands, the only way out for the lone
 * owner is to delete/clear the workspace.
 */
@Single
class LeaveWorkspaceUseCase(
    private val memberRepository: WorkspaceMemberRepository,
    private val session: SessionPointers,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(workspaceId: WorkspaceId): Either<InviteError, Unit> = either {
        val callerId = session.currentUserId.first()
            ?: raise(InviteError.NoCurrentUser)

        val members = memberRepository.getByWorkspaceId(workspaceId).first()
        val self = members.firstOrNull { it.userId == callerId }
            ?: raise(InviteError.InviteNotFound)
        ensure(self.status == WorkspaceMemberStatus.ACTIVE) { InviteError.InviteNotPending }

        if (self.role == WorkspaceRole.OWNER) {
            val otherActiveOwners = members.count {
                it.role == WorkspaceRole.OWNER &&
                    it.status == WorkspaceMemberStatus.ACTIVE &&
                    it.userId != callerId
            }
            ensure(otherActiveOwners >= 1) { InviteError.OwnerCannotLeave }
        }

        Either.catch { memberRepository.markLeft(callerId, workspaceId) }
            .onLeft { log.e(it) { "[local] markLeft failed uid=${callerId.value.redactUid()}" } }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        log.i { "[done] uid=${callerId.value.redactUid()} wid=${workspaceId.value}" }
    }

    private companion object {
        const val TAG = "LeaveWorkspace"
    }
}
