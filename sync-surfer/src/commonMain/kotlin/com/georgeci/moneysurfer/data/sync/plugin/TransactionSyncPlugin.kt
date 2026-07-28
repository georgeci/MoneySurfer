package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.remote.TransactionDoc
import com.georgeci.moneysurfer.data.sync.toDoc
import com.georgeci.moneysurfer.data.sync.toEntity
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.ConflictMetadata
import com.georgeci.moneysurfer.sync.repository.ConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single(binds = [SyncEntityPlugin::class])
class TransactionSyncPlugin(
    private val firestore: FirebaseFirestore,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val transactionDao: TransactionDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.TRANSACTION
    override val firestoreCollectionName: String = SyncCollection.TRANSACTIONS
    override val pullPriority: Int = SyncPullPriorities.TRANSACTIONS

    override suspend fun push(mutation: PendingMutation) {
        val docRef = workspaceCollection(mutation.scopeKey!!).document(mutation.entityId)
        when (mutation.operation) {
            MutationOperation.INSERT,
            MutationOperation.UPDATE,
            -> {
                val entity = transactionDao.getById(mutation.entityId) ?: return
                docRef.set(entity.toDoc().copy(clientVersionCode = appInfo.versionCode))
            }
            MutationOperation.DELETE -> docRef.pushTombstone(
                tombstonePatchFor(mutation, clientVersionCode = appInfo.versionCode),
            )
        }
    }

    /**
     * A pulled tombstone marks the local row deleted instead of dropping it (issue #346), which
     * makes a remote delete and a local one land in exactly the same state — and leaves the peer's
     * delete just as recoverable as this device's own.
     *
     * It is a targeted UPDATE rather than an upsert of the decoded doc: a tombstone patch carries
     * only `deletedAt`, `updatedAt` and `clientVersionCode`, so upserting what it decodes to would
     * write a row of defaults over real data — and would conjure one out of nothing, failing the
     * account foreign key, on a device that never held the row. A device that never had it has
     * nothing to forget, and `softDelete` no-ops there.
     *
     * The tombstone wins unconditionally, without consulting the resolver — as the hard delete it
     * replaces did. The other direction still goes through last-writer-wins: a remote doc with no
     * `deletedAt` and a newer `updatedAt` than the local delete clears the tombstone through the
     * upsert below, which is a peer's edit legitimately outranking this device's delete.
     *
     * `softDelete` also copies the remote `deletedAt` onto `updatedAt` — the same value the
     * tombstone patch wrote remotely — so re-pulling the doc is a tie, resolves to `TakeLocal` and
     * changes nothing.
     */
    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(TransactionDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        val remoteDeletedAt = dto.deletedAt
        if (remoteDeletedAt != null) {
            transactionDao.softDelete(doc.id, remoteDeletedAt)
            return EntityApplyResult(applied = true, wasConflict = false)
        }
        // Includes tombstoned rows on purpose: a locally-deleted row that is invisible here reads
        // as "no local copy", and the resolver would take an older remote doc as a fresh insert —
        // resurrecting exactly what the user just deleted.
        val local = transactionDao.getByIdIncludingDeleted(doc.id)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(id = doc.id, workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.TRANSACTION,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { transactionDao.upsertAll(listOf(it)) }
    }

    private fun workspaceCollection(workspaceId: String) =
        firestore.collection("workspaces").document(workspaceId).collection(SyncCollection.TRANSACTIONS)
}
