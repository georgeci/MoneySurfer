package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.data.db.dao.CategoryDao
import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.remote.CategoryDoc
import com.georgeci.moneysurfer.data.sync.toDoc
import com.georgeci.moneysurfer.data.sync.toEntity
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
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
class CategorySyncPlugin(
    private val firestore: FirebaseFirestore,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.CATEGORY
    override val firestoreCollectionName: String = "categories"
    override val pullPriority: Int = SyncPullPriorities.CATEGORIES

    override suspend fun push(mutation: PendingMutation) {
        val docRef = workspaceCollection(mutation.scopeKey!!).document(mutation.entityId)
        when (mutation.operation) {
            MutationOperation.INSERT,
            MutationOperation.UPDATE,
            -> {
                val entity = categoryDao.getById(mutation.entityId) ?: return
                docRef.set(entity.toDoc().copy(clientVersionCode = appInfo.versionCode))
            }
            MutationOperation.DELETE -> docRef.pushTombstone(
                tombstonePatchFor(mutation, clientVersionCode = appInfo.versionCode),
            )
        }
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(CategoryDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        if (dto.deletedAt != null) {
            transactionDao.nullifyCategoryId(doc.id)
            categoryDao.delete(doc.id)
            return EntityApplyResult(applied = true, wasConflict = false)
        }
        val local = categoryDao.getById(doc.id)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(id = doc.id, workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.CATEGORY,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { categoryDao.upsertAll(listOf(it)) }
    }

    private fun workspaceCollection(workspaceId: String) =
        firestore.collection("workspaces").document(workspaceId).collection("categories")
}
