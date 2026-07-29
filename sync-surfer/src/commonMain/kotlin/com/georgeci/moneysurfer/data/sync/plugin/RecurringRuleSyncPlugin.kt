package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.data.db.dao.RecurringRuleDao
import com.georgeci.moneysurfer.data.remote.RecurringRuleDoc
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

/**
 * Replicates recurring rules so `TransactionEntity.recurringRuleId` resolves on every device.
 *
 * Pulls at [SyncPullPriorities.RECURRING_RULES] — after categories (the rule's Room FK) and
 * before transactions, so a transaction arriving in the same sync already has its rule locally.
 */
@Single(binds = [SyncEntityPlugin::class])
class RecurringRuleSyncPlugin(
    private val writer: WorkspaceDocumentWriter,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val recurringRuleDao: RecurringRuleDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.RECURRING_RULE
    override val firestoreCollectionName: String = SyncCollection.RECURRING_RULES
    override val pullPriority: Int = SyncPullPriorities.RECURRING_RULES

    override suspend fun push(mutation: PendingMutation) = writer.pushToCollection(
        mutation = mutation,
        collectionName = SyncCollection.RECURRING_RULES,
        clientVersionCode = appInfo.versionCode,
        strategy = RecurringRuleDoc.serializer(),
    ) {
        recurringRuleDao.getById(mutation.entityId)
            ?.toDoc()
            ?.copy(clientVersionCode = appInfo.versionCode)
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(RecurringRuleDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        if (dto.deletedAt != null) {
            recurringRuleDao.delete(doc.id)
            return EntityApplyResult(applied = true, wasConflict = false)
        }
        val local = recurringRuleDao.getById(doc.id)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(id = doc.id, workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.RECURRING_RULE,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { recurringRuleDao.upsertAll(listOf(it)) }
    }
}
