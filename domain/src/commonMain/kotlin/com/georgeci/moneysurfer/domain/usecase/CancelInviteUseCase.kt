package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Owner cancels a pending invitation before the recipient responds. Owner-only.
 */
@Single
class CancelInviteUseCase(
    private val inviteRepository: WorkspaceInviteRepository,
    private val memberRepository: WorkspaceMemberRepository,
    private val session: SessionPointers,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(id: WorkspaceInviteId): Either<InviteError, Unit> = either {
        val callerId = session.currentUserId.flow.first()
            ?: raise(InviteError.NoCurrentUser)

        val invite = inviteRepository.getById(id)
            ?: raise(InviteError.InviteNotFound)
        ensure(invite.status == InviteStatus.PENDING) { InviteError.InviteNotPending }

        val callerMember = memberRepository.getById(callerId, invite.workspaceId)
        ensure(callerMember?.role == WorkspaceRole.OWNER) { InviteError.NotOwner }

        Either.catch { inviteRepository.cancel(id) }
            .onLeft { log.e(it) { "[local] cancel failed id=${id.value}" } }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        log.i { "[done] uid=${callerId.value.redactUid()} id=${id.value}" }
    }

    private companion object {
        const val TAG = "CancelInvite"
    }
}
