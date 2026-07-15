package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Recipient declines a pending invitation. No member row created; invite transitions to DECLINED.
 */
@Single
class DeclineInviteUseCase(
    private val inviteRepository: WorkspaceInviteRepository,
    private val userRepository: UserRepository,
    private val userRemoteRepository: UserRemoteRepository,
    private val session: SessionPointers,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(id: WorkspaceInviteId): Either<InviteError, Unit> = either {
        val callerId = session.currentUserId.flow.first()
            ?: raise(InviteError.NoCurrentUser)
        val caller = userRepository.getById(callerId)
            ?: raise(InviteError.NoCurrentUser)

        val invite = inviteRepository.getById(id)
            ?: raise(InviteError.InviteNotFound)
        ensure(invite.status == InviteStatus.PENDING) { InviteError.InviteNotPending }

        val emailMatches = caller.email?.lowercase() == invite.email.lowercase()
        val uidMatches = invite.targetUserId == callerId
        ensure(emailMatches || uidMatches) { InviteError.InviteNotFound }

        Either.catch { inviteRepository.markDeclined(id) }
            .onLeft { log.e(it) { "[local] markDeclined failed id=${id.value}" } }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        val firebaseUid = session.currentFirebaseUid.flow.first()
        if (!firebaseUid.isNullOrBlank()) {
            Either.catch {
                userRemoteRepository.removeInvitedWorkspaceRef(firebaseUid, invite.workspaceId)
            }.onLeft {
                log.w(it) { "[remote] removeInvitedWorkspaceRef failed — non-fatal" }
            }
        }

        log.i { "[done] uid=${callerId.value.redactUid()} id=${id.value}" }
    }

    private companion object {
        const val TAG = "DeclineInvite"
    }
}
