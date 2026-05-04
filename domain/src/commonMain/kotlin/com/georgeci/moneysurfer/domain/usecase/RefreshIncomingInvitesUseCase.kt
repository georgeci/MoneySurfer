package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import org.koin.core.annotation.Single

/**
 * Triggers a full user-data sync that includes pulling invites from workspaces listed
 * in `users/{uid}.invitedWorkspaceIds`. Replaces the previous collectionGroup-based
 * [IncomingInviteRemoteRepository] approach.
 */
@Single
class RefreshIncomingInvitesUseCase(
    private val workspaceSyncer: WorkspaceSyncer,
) {
    suspend operator fun invoke(): Either<InviteError, Int> =
        Either.catch { workspaceSyncer.syncAll() }
            .mapLeft { InviteError.RemoteSyncFailed(it) }
            .map { 0 } // count not available from syncAll
}
