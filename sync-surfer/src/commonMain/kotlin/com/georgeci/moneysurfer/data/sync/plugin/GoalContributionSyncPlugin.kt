package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.data.db.dao.GoalContributionDao
import com.georgeci.moneysurfer.data.db.dao.GoalDao
import com.georgeci.moneysurfer.data.remote.GoalContributionDoc
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
class GoalContributionSyncPlugin(
    private val writer: WorkspaceDocumentWriter,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val contributionDao: GoalContributionDao,
    private val goalDao: GoalDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.GOAL_CONTRIBUTION
    override val firestoreCollectionName: String = SyncCollection.GOAL_CONTRIBUTIONS
    override val pullPriority: Int = SyncPullPriorities.GOAL_CONTRIBUTIONS

    override suspend fun push(mutation: PendingMutation) = writer.pushToCollection(
        mutation = mutation,
        collectionName = SyncCollection.GOAL_CONTRIBUTIONS,
        clientVersionCode = appInfo.versionCode,
        strategy = GoalContributionDoc.serializer(),
    ) {
        contributionDao.getById(mutation.entityId)
            ?.toDoc()
            ?.copy(clientVersionCode = appInfo.versionCode)
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(GoalContributionDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        return when {
            dto.deletedAt != null -> {
                contributionDao.delete(doc.id)
                EntityApplyResult(applied = true, wasConflict = false)
            }
            // Hard Room FK on goalId. Goals pull first (priority 60 < 70), so the parent is
            // normally present — but a contribution whose goal was deleted on this device can
            // still arrive before its own tombstone does, and inserting it would raise an FK
            // violation that aborts the batch BEFORE the cursor advances, wedging every later
            // pull. Skipping advances the cursor and drops a row with no goal to belong to.
            goalDao.getById(dto.goalId) == null -> SKIPPED_APPLY_RESULT
            else -> resolveAndApply(doc, dto, scopeKey)
        }
    }

    private suspend fun resolveAndApply(
        doc: RemoteDocument,
        dto: GoalContributionDoc,
        scopeKey: String,
    ): EntityApplyResult {
        val local = contributionDao.getById(doc.id)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(id = doc.id, workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.GOAL_CONTRIBUTION,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { contributionDao.upsertAll(listOf(it)) }
    }
}
