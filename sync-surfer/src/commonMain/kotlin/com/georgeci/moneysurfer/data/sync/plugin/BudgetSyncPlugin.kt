package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.data.db.dao.BudgetDao
import com.georgeci.moneysurfer.data.remote.BudgetDoc
import com.georgeci.moneysurfer.data.sync.WorkspaceDocumentWriter
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
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single(binds = [SyncEntityPlugin::class])
class BudgetSyncPlugin(
    private val writer: WorkspaceDocumentWriter,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val budgetDao: BudgetDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.BUDGET
    override val firestoreCollectionName: String = SyncCollection.BUDGETS
    override val pullPriority: Int = SyncPullPriorities.BUDGETS

    override suspend fun push(mutation: PendingMutation) = writer.pushToCollection(
        mutation = mutation,
        collectionName = SyncCollection.BUDGETS,
        clientVersionCode = appInfo.versionCode,
        strategy = BudgetDoc.serializer(),
    ) {
        budgetDao.getById(mutation.entityId)
            ?.toDoc()
            ?.copy(clientVersionCode = appInfo.versionCode)
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(BudgetDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        if (dto.deletedAt != null) {
            budgetDao.delete(doc.id)
            return EntityApplyResult(applied = true, wasConflict = false)
        }
        val local = budgetDao.getById(doc.id)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(id = doc.id, workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.BUDGET,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { budgetDao.upsertAll(listOf(it)) }
    }
}
