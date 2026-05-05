package com.georgeci.moneysurfer.domain.repositories

/**
 * Wipes the sync-side ledgers (pending_mutations, sync_meta) so a fresh
 * post-logout session doesn't reattempt the previous user's outbox or
 * resume from their cursor positions.
 *
 * Lives separately from [LocalDataResetRepository] so the offline build
 * can stay unaware of the sync database — it provides a no-op binding
 * while still satisfying [LogoutUseCase]'s contract.
 */
interface RemoteDataResetRepository {
    suspend fun clearAll()
}
