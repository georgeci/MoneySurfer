package com.georgeci.moneysurfer.data.sync.plugin

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.data.db.entity.ConfigEntryEntity
import com.georgeci.moneysurfer.data.remote.UserConfigDoc
import com.georgeci.moneysurfer.data.sync.UserConfigRemoteSource
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.PullScope
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.ConflictMetadata
import com.georgeci.moneysurfer.sync.repository.ConflictResolution
import com.georgeci.moneysurfer.sync.repository.ConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.time.Instant

/**
 * Replicates the settings marked `sync = true` between a user's devices.
 *
 * **One key is one entity.** Outbox rows carry no payload — `push` re-reads current local state —
 * so there is no field-merge concept to support and the mapping falls out naturally:
 *
 * | | |
 * | --- | --- |
 * | `entityType` | [SyncEntityTypes.USER_CONFIG] |
 * | `entityId` | the key name, e.g. `ui.theme_mode` |
 * | `scopeKey` | `null` — settings belong to the user, not to a workspace |
 * | Firestore path | `users/{uid}/config/{keyName}` |
 *
 * Per-key LWW then needs no new primitives: two devices changing *different* settings touch
 * different documents and both survive, and the same setting resolves by `updatedAt` through the
 * shared [ConflictResolver].
 *
 * It lives here with the other entity plugins rather than in `app-config/remote` because it is a
 * sync plugin first: it needs `PluginHelpers`, the resolver and the outbox, and it has nothing to
 * do with the RemoteGlobal layer.
 */
@Single(binds = [SyncEntityPlugin::class])
class UserConfigSyncPlugin(
    private val remote: UserConfigRemoteSource,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val configEntryDao: ConfigEntryDao,
    private val session: SessionPointers,
) : SyncEntityPlugin {

    private val log = Logger.withTag(TAG)

    override val entityType: String = SyncEntityTypes.USER_CONFIG
    override val firestoreCollectionName: String = SyncCollection.USER_CONFIG
    override val pullScope: PullScope = PullScope.User
    override val pullPriority: Int = SyncPullPriorities.DEFAULT

    /**
     * Pushes whatever the row holds *now*, then records it as pushed — but only if the row still
     * carries the `updatedAt` that was sent, so a write that landed between the read and the write
     * stays pending instead of being marked as delivered.
     *
     * A missing row is a no-op rather than an error: the account wipe deletes `config_entry` while
     * outbox rows for it may still be queued, and a wiped setting must not be resurrected on the
     * server from a stale queue entry.
     *
     * A missing uid, in contrast, **throws**. `UploadPendingChangesUseCaseImpl` treats any push
     * that returns normally as delivered and deletes the row, so returning quietly here would drop
     * the queue entry for a setting that never reached the server — recovered only at the next
     * session start, and silently unsynced until then. Throwing routes it to `markFailed`, which is
     * what puts it back to `PENDING` for the next drain.
     */
    override suspend fun push(mutation: PendingMutation) {
        if (mutation.operation == MutationOperation.DELETE) {
            // Nothing enqueues this: a setting is overwritten, never deleted. The account-deletion
            // purge clears the collection directly, outside the outbox.
            log.w { "[push] ignoring DELETE for ${mutation.entityId} — settings are not deletable" }
            return
        }
        val uid = session.currentFirebaseUid.first()
        check(!uid.isNullOrEmpty()) {
            "no Firebase session — ${mutation.entityId} stays queued for the next drain"
        }
        val row = configEntryDao.getByKey(mutation.entityId) ?: return

        remote.write(
            uid = uid,
            key = row.key,
            doc = UserConfigDoc(
                value = row.value,
                updatedAt = row.updatedAt,
                clientVersionCode = appInfo.versionCode,
            ),
        )
        configEntryDao.markPushed(key = row.key, pushedUpdatedAt = row.updatedAt)
    }

    /**
     * [scopeKey] is the uid here, not a workspace id — this plugin is [PullScope.User].
     *
     * "Remote is not newer" is the *normal* outcome, not a conflict: the user-scoped phase has no
     * cursor, so every pull re-reads every document, and reporting each unchanged one as a conflict
     * would make the sync summary meaningless. Only a value this device actually adopts counts.
     *
     * A key this build does not know is stored anyway. It costs one row, it is what a mixed-version
     * pair of devices needs in order not to lose each other's settings, and resolution never looks
     * up a name that has no key object.
     */
    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(UserConfigDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        val local = configEntryDao.getByKey(doc.id)
        val remoteRow = ConfigEntryEntity(
            key = doc.id,
            value = dto.value,
            updatedAt = dto.updatedAt,
            // The server is where this value came from, so it is by definition already pushed —
            // otherwise the sign-in reconciliation would push it straight back.
            lastPushedAt = dto.updatedAt,
        )
        val resolution = conflictResolver.resolve(
            local = local,
            remote = remoteRow,
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.USER_CONFIG,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        if (resolution !is ConflictResolution.TakeRemote) return SKIPPED_APPLY_RESULT

        configEntryDao.applyRemote(
            key = remoteRow.key,
            value = remoteRow.value,
            updatedAt = remoteRow.updatedAt,
        )
        return EntityApplyResult(applied = true, wasConflict = false)
    }

    private companion object {
        const val TAG = "UserConfigSync"
    }
}
