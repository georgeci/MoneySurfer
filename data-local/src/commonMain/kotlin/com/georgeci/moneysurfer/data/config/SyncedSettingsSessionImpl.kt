package com.georgeci.moneysurfer.data.config

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.appconfig.SessionConfigOverlay
import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.domain.config.SyncedSettingsSession
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import org.koin.core.annotation.Single

/**
 * Clears the logout overlay and replays the pushes the outbox refused. See [SyncedSettingsSession].
 *
 * Ordering inside is deliberate: the overlay goes first, so that even if the reconciliation throws,
 * the previous user's values are already gone rather than shadowing the new session for as long as
 * the process lives.
 */
@Single(binds = [SyncedSettingsSession::class])
class SyncedSettingsSessionImpl(
    private val overlay: SessionConfigOverlay,
    private val configEntryDao: ConfigEntryDao,
    private val outbox: OutboxEnqueuer,
) : SyncedSettingsSession {

    private val log = Logger.withTag(TAG)

    override suspend fun onSessionStart() {
        overlay.clear()

        val pending = configEntryDao.keysPendingPush()
        if (pending.isEmpty()) return
        log.i { "[reconcile] queueing ${pending.size} setting(s) written while sync was unavailable" }
        pending.forEach { key ->
            outbox.enqueueUpsert(
                entityType = SyncEntityTypes.USER_CONFIG,
                entityId = key,
                scopeKey = null,
                operation = MutationOperation.UPDATE,
            )
        }
    }

    private companion object {
        const val TAG = "SyncedSettings"
    }
}
