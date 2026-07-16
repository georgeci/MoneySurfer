package com.georgeci.moneysurfer.data.sync.plugin
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.remote.WorkspaceInviteDoc
import com.georgeci.moneysurfer.data.sync.toDoc
import com.georgeci.moneysurfer.data.sync.toEntity
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
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
class WorkspaceInviteSyncPlugin(
    private val firestore: FirebaseFirestore,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val workspaceInviteDao: WorkspaceInviteDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.WORKSPACE_INVITE
    override val firestoreCollectionName: String = "invites"
    override val pullPriority: Int = SyncPullPriorities.INVITES

    private val log = Logger.withTag("WorkspaceInvitePlugin")

    override suspend fun push(mutation: PendingMutation) {
        val docRef = workspaceCollection(mutation.scopeKey!!).document(mutation.entityId)
        when (mutation.operation) {
            MutationOperation.INSERT,
            MutationOperation.UPDATE,
            -> {
                val entity = workspaceInviteDao.getById(mutation.entityId) ?: return
                val dto = entity.toDoc().copy(clientVersionCode = appInfo.versionCode)
                log.i {
                    "[push:invite] path=${docRef.path} email=${dto.email.redactEmail()} " +
                        "status=${dto.status} role=${dto.role} " +
                        "target=${dto.targetUserId.redactUid()} invitedBy=${dto.invitedByUserId.redactUid()} " +
                        "expiresAt=${dto.expiresAt} updatedAt=${dto.updatedAt} ver=${dto.clientVersionCode}"
                }
                docRef.set(dto)
            }
            MutationOperation.DELETE -> docRef.pushTombstone(
                tombstonePatchFor(mutation, clientVersionCode = appInfo.versionCode),
            )
        }
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(WorkspaceInviteDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        if (dto.deletedAt != null) {
            workspaceInviteDao.delete(doc.id)
            return EntityApplyResult(applied = true, wasConflict = false)
        }
        val local = workspaceInviteDao.getById(doc.id)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(id = doc.id, workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.WORKSPACE_INVITE,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { workspaceInviteDao.upsert(it) }
    }

    private fun workspaceCollection(workspaceId: String) =
        firestore.collection("workspaces").document(workspaceId).collection("invites")
}
