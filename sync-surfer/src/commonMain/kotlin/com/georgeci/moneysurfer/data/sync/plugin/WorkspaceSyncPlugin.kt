package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.data.db.dao.UserDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceDao
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.remote.WorkspaceDoc
import com.georgeci.moneysurfer.data.sync.WorkspaceRefRegistrar
import com.georgeci.moneysurfer.data.sync.toDoc
import com.georgeci.moneysurfer.data.sync.toEntity
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single

/**
 * Handles workspace root-doc push and pull.
 *
 * [isWorkspaceRootPlugin] = true signals [PullRemoteChangesUseCaseImpl] to call
 * [applyDoc] with the workspace root document before any subcollection plugin runs.
 * This guarantees the local [WorkspaceEntity] exists when accounts / categories /
 * transactions are upserted (all carry a Room FK workspaceId → WorkspaceEntity).
 *
 * [firestoreCollectionName] remains null — root docs are not a subcollection and
 * are not fetched via [WorkspaceCollectionReader.fetchUpdatedSince].
 *
 * [applyDoc] also stubs the owner's [UserEntity] via insertIgnore so the
 * WorkspaceEntity FK ownerId → UserEntity is satisfied even on a fresh device
 * before the members collection is pulled.
 */
@Single(binds = [SyncEntityPlugin::class])
class WorkspaceSyncPlugin(
    private val firestore: FirebaseFirestore,
    private val appInfo: AppInfo,
    private val workspaceDao: WorkspaceDao,
    private val userDao: UserDao,
    private val workspaceRefRegistrar: WorkspaceRefRegistrar,
) : SyncEntityPlugin {

    override val entityType: String = SyncEntityTypes.WORKSPACE
    override val firestoreCollectionName: String? = null
    override val pullPriority: Int = SyncPullPriorities.WORKSPACE

    override suspend fun push(mutation: PendingMutation) {
        val docRef = firestore.collection("workspaces").document(mutation.entityId)
        when (mutation.operation) {
            MutationOperation.INSERT,
            MutationOperation.UPDATE,
            -> {
                val entity = workspaceDao.getById(mutation.entityId) ?: return
                docRef.set(entity.toDoc().copy(clientVersionCode = appInfo.versionCode))
                // The ref travels with the document. A workspace created while sync was
                // disabled has no `users/{uid}.workspaceIds` entry and nothing else would ever
                // add one, leaving it invisible to every other device (issue #342).
                workspaceRefRegistrar.register(mutation.entityId)
            }
            MutationOperation.DELETE -> docRef.pushTombstone(
                tombstonePatchFor(mutation, clientVersionCode = appInfo.versionCode),
            )
        }
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        val dto = doc.decodeOrNull(WorkspaceDoc.serializer()) ?: return SKIPPED_APPLY_RESULT
        if (dto.deletedAt != null) {
            // Soft-delete: workspace is tombstoned. Do not remove from Room —
            // the sync layer handles cleanup; just skip the upsert.
            return EntityApplyResult(applied = false, wasConflict = false)
        }
        // Stub the owner's UserEntity so the FK ownerId → UserEntity is satisfied
        // before workspaceDao.upsertAll(). insertIgnore is a no-op if the row exists.
        userDao.insertIgnore(UserEntity(id = dto.ownerId, displayName = null, isAnon = false))
        workspaceDao.upsertAll(listOf(dto.toEntity(id = doc.id)))
        return EntityApplyResult(applied = true, wasConflict = false)
    }
}
