package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.data.db.dao.UserDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.remote.WorkspaceMemberDoc
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
class WorkspaceMemberSyncPlugin(
    private val firestore: FirebaseFirestore,
    private val appInfo: AppInfo,
    private val conflictResolver: ConflictResolver,
    private val workspaceMemberDao: WorkspaceMemberDao,
    private val workspaceInviteDao: WorkspaceInviteDao,
    private val userDao: UserDao,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.WORKSPACE_MEMBER
    override val firestoreCollectionName: String = "members"
    override val pullPriority: Int = SyncPullPriorities.MEMBERS

    override suspend fun push(mutation: PendingMutation) {
        val scopeKey = mutation.scopeKey!!
        val docRef = workspaceCollection(scopeKey).document(mutation.entityId)
        when (mutation.operation) {
            MutationOperation.INSERT,
            MutationOperation.UPDATE,
            -> {
                val entity = workspaceMemberDao.getById(
                    userId = mutation.entityId,
                    workspaceId = scopeKey,
                ) ?: return
                // Stamp the admitting invite so the rules can authorize the accept-invite
                // self-create (issue #152). Null for owner-created rows — the owner branch
                // in the rules does not consult it.
                val inviteId = workspaceInviteDao.findJoinableInviteId(
                    workspaceId = scopeKey,
                    userId = entity.userId,
                    email = entity.email,
                )
                docRef.set(
                    entity.toDoc().copy(
                        clientVersionCode = appInfo.versionCode,
                        inviteId = inviteId,
                    ),
                )
            }
            MutationOperation.DELETE -> docRef.delete()
        }
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decode(WorkspaceMemberDoc.serializer())
        if (dto.deletedAt != null) {
            workspaceMemberDao.delete(userId = doc.id, workspaceId = scopeKey)
            return EntityApplyResult(applied = true, wasConflict = false)
        }
        userDao.insertIgnore(
            UserEntity(
                id = doc.id,
                displayName = dto.displayName.ifEmpty { null },
                isAnon = false,
            ),
        )
        val local = workspaceMemberDao.getById(userId = doc.id, workspaceId = scopeKey)
        val resolution = conflictResolver.resolve(
            local = local,
            remote = dto.toEntity(workspaceId = scopeKey),
            metadata = ConflictMetadata(
                entityType = SyncEntityTypes.WORKSPACE_MEMBER,
                entityId = doc.id,
                localUpdatedAt = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
                remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
            ),
        )
        return applyResolution(resolution) { workspaceMemberDao.upsert(it) }
    }

    private fun workspaceCollection(workspaceId: String) =
        firestore.collection("workspaces").document(workspaceId).collection("members")
}
