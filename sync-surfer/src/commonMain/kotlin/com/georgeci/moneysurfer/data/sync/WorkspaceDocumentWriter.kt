package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.sync.plugin.TombstonePatch
import com.georgeci.moneysurfer.sync.api.SyncCollection
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.SerializationStrategy
import org.koin.core.annotation.Single

/**
 * A document somewhere under `workspaces/…`, addressed by its full path.
 *
 * A path rather than a `(workspaceId, collection, id)` triple because the workspace root document
 * has no collection and no id of its own, and a nullable-collection triple would have to be
 * re-checked at every call site.
 */
data class WorkspaceDocRef(val path: String) {

    companion object {
        /** `workspaces/{workspaceId}` — the workspace's own document. */
        fun root(workspaceId: String): WorkspaceDocRef =
            WorkspaceDocRef("${SyncCollection.WORKSPACES}/$workspaceId")

        /** `workspaces/{workspaceId}/{collectionName}/{documentId}`. */
        fun inCollection(
            workspaceId: String,
            collectionName: String,
            documentId: String,
        ): WorkspaceDocRef =
            WorkspaceDocRef("${SyncCollection.WORKSPACES}/$workspaceId/$collectionName/$documentId")
    }
}

/**
 * The write half of the workspace tree — the counterpart of [WorkspaceCollectionReader], and the
 * same seam [UserConfigRemoteSource] is.
 *
 * What is worth testing about a push is which document it addresses and what it puts there, and
 * gitlive's client cannot be constructed off Android (its "JVM" artefact casts to
 * `android.content.Context`). Taking this interface rather than `FirebaseFirestore` is what lets a
 * plugin's push half be exercised on the JVM host instead of only on a device.
 */
interface WorkspaceDocumentWriter {

    /** Overwrites the document at [ref] with [value]. */
    suspend fun <T : Any> set(ref: WorkspaceDocRef, strategy: SerializationStrategy<T>, value: T)

    /**
     * Writes [patch] onto an existing document, skipping one that never reached Firestore: an
     * entity created and deleted between two drains leaves the INSERT push a no-op (its `getById`
     * finds no live row), and updating the missing doc would raise NOT_FOUND on every retry,
     * wedging the batch. A doc that never existed remotely has nothing for peers to forget.
     */
    suspend fun tombstone(ref: WorkspaceDocRef, patch: TombstonePatch)
}

@Single(binds = [WorkspaceDocumentWriter::class])
class FirebaseWorkspaceDocumentWriter(
    private val firestore: FirebaseFirestore,
) : WorkspaceDocumentWriter {

    override suspend fun <T : Any> set(
        ref: WorkspaceDocRef,
        strategy: SerializationStrategy<T>,
        value: T,
    ) {
        firestore.document(ref.path).set(strategy, value)
    }

    override suspend fun tombstone(ref: WorkspaceDocRef, patch: TombstonePatch) {
        val document = firestore.document(ref.path)
        if (!document.get().exists) return
        document.update(TombstonePatch.serializer(), patch)
    }
}
