package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single

@Single(binds = [WorkspaceCollectionReader::class])
class FirebaseWorkspaceCollectionReader(
    private val firestore: FirebaseFirestore,
) : WorkspaceCollectionReader {

    override suspend fun fetchWorkspaceDoc(workspaceId: String): RemoteDocument? {
        val snap = firestore.collection(SyncCollection.WORKSPACES).document(workspaceId).get()
        return if (snap.exists) FirebaseRemoteDocument(snap) else null
    }

    override suspend fun fetchUpdatedSince(
        workspaceId: String,
        collectionName: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> =
        firestore
            .collection(SyncCollection.WORKSPACES)
            .document(workspaceId)
            .collection(collectionName)
            .where { "updatedAt" greaterThan sinceMillis }
            .orderBy(field = "updatedAt")
            .limit(limit.toLong())
            .get()
            .documents
            .map { FirebaseRemoteDocument(it) }

    override suspend fun fetchInvitesForUser(
        workspaceId: String,
        uid: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> =
        firestore
            .collection(SyncCollection.WORKSPACES)
            .document(workspaceId)
            .collection(SyncCollection.WORKSPACE_INVITES)
            .where { "targetUserId" equalTo uid }
            .where { "updatedAt" greaterThan sinceMillis }
            .orderBy(field = "updatedAt")
            .limit(limit.toLong())
            .get()
            .documents
            .map { FirebaseRemoteDocument(it) }
}
