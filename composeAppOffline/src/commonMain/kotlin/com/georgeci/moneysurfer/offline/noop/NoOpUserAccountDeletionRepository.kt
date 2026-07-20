package com.georgeci.moneysurfer.offline.noop

import arrow.core.Either
import arrow.core.left
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.repositories.UserAccountDeletionRepository

/**
 * Offline build has no remote account to delete — the Settings entry point is hidden via
 * [OfflineBuildFlags][com.georgeci.moneysurfer.domain.OfflineBuildFlags]; this binding only
 * keeps the Koin graph resolvable for `DeleteUserAccountUseCase`.
 */
class NoOpUserAccountDeletionRepository : UserAccountDeletionRepository {
    override suspend fun deleteRemoteUserData(
        uid: String,
        email: String?,
    ): Either<AccountDeletionError, Unit> = AccountDeletionError.NotSignedIn.left()
}
