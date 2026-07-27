package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.remote.UserConfigDoc
import com.georgeci.moneysurfer.sync.api.SyncCollection
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single

/**
 * The one write of the per-user settings sync: `users/{uid}/config/{key}`.
 *
 * A seam rather than a direct `firestore` call inside the plugin, for the reason
 * [AccountDeletionRemoteSource][com.georgeci.moneysurfer.data.remote.AccountDeletionRemoteSource]
 * exists: the decision logic around the write — what to push, when a row is stale, when a push may
 * be recorded — is the part worth testing, and gitlive's JVM artefact cannot be instantiated off
 * Android.
 */
interface UserConfigRemoteSource {

    suspend fun write(uid: String, key: String, doc: UserConfigDoc)
}

@Single(binds = [UserConfigRemoteSource::class])
class FirebaseUserConfigRemoteSource(
    private val firestore: FirebaseFirestore,
) : UserConfigRemoteSource {

    override suspend fun write(uid: String, key: String, doc: UserConfigDoc) {
        firestore
            .collection(SyncCollection.USERS)
            .document(uid)
            .collection(SyncCollection.USER_CONFIG)
            .document(key)
            .set(doc)
    }
}
