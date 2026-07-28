package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single

@Single(binds = [UserCollectionReader::class])
class FirebaseUserCollectionReader(
    private val firestore: FirebaseFirestore,
) : UserCollectionReader {

    /**
     * No `where` and no `orderBy` — only the cap, so the Firestore rule still has to permit nothing
     * more than a bare `list`. The cap bounds the read; it does not paginate, because a truncated
     * settings collection is a bug to report, not a page to fetch.
     */
    override suspend fun fetchAll(
        uid: String,
        collectionName: String,
        limit: Int,
    ): List<RemoteDocument> =
        firestore
            .collection(SyncCollection.USERS)
            .document(uid)
            .collection(collectionName)
            .limit(limit.toLong())
            .get()
            .documents
            .map { FirebaseRemoteDocument(it) }
}
