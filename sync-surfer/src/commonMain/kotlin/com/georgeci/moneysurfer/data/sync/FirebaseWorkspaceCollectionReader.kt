package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.data.sync.WorkspaceCollectionReader
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single

@Single(binds = [WorkspaceCollectionReader::class])
class FirebaseWorkspaceCollectionReader(
    private val firestore: FirebaseFirestore,
) : WorkspaceCollectionReader {

    override suspend fun fetchWorkspaceDoc(workspaceId: String): RemoteDocument? {
        val snap = firestore.collection("workspaces").document(workspaceId).get()
        return if (snap.exists) FirebaseRemoteDocument(snap) else null
    }

    override suspend fun fetchUpdatedSince(
        workspaceId: String,
        collectionName: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> =
        firestore
            .collection("workspaces")
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
            .collection("workspaces")
            .document(workspaceId)
            .collection("invites")
            .where { "targetUserId" equalTo uid }
            .where { "updatedAt" greaterThan sinceMillis }
            .orderBy(field = "updatedAt")
            .limit(limit.toLong())
            .get()
            .documents
            .map { FirebaseRemoteDocument(it) }
}
