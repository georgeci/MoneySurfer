package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

/**
 * Streams pending invitations addressed to the current user — by `email` (lowercase match)
 * AND by `targetUserId`. Expired-but-still-PENDING rows are surfaced as PENDING; UI tags
 * them as EXPIRED for display when `now > expiresAt`.
 *
 * Empty when no current user — caller usually doesn't render anything in that state.
 */
@Single
class ListPendingInvitesForCurrentUserUseCase(
    private val inviteRepository: WorkspaceInviteRepository,
    private val userRepository: UserRepository,
    private val session: SessionPointers,
) {
    operator fun invoke(): Flow<List<WorkspaceInvite>> = flow {
        val callerId = session.currentUserId.flow.first()
        if (callerId == null) {
            emit(emptyList())
            return@flow
        }
        val caller = userRepository.getById(callerId)
        val email = caller?.email?.lowercase()

        val byEmail = if (email != null) inviteRepository.getByEmail(email) else flowOf(emptyList())
        val byUid = inviteRepository.getByTargetUserId(callerId)

        emitAll(
            byEmail.combine(byUid) { a, b ->
                (a + b)
                    .distinctBy { it.id.value }
                    .filter { it.status == InviteStatus.PENDING }
            },
        )
    }
}
