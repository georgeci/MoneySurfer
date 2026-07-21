package com.georgeci.moneysurfer.data.remote

import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import org.koin.core.annotation.Single

/**
 * The only Firebase-aware part of the account-deletion purge: maps [AccountDeletionRemoteSource]
 * primitives onto Firestore document paths, DTOs and write batches. Deliberately free of
 * decision logic — what gets purged versus left is decided in
 * [UserAccountDeletionRepositoryImpl][com.georgeci.moneysurfer.data.repository.UserAccountDeletionRepositoryImpl].
 */
@Single(binds = [AccountDeletionRemoteSource::class])
class FirebaseAccountDeletionRemoteSource(
    private val firestore: FirebaseFirestore,
) : AccountDeletionRemoteSource {

    override suspend fun fetchWorkspaceIds(uid: String): List<String> = denyingAware {
        val snap = userRef(uid).get()
        if (!snap.exists) emptyList() else snap.data(UserDoc.serializer()).workspaceIds.distinct()
    }

    override suspend fun fetchWorkspaceOwnerId(workspaceId: String): String? = denyingAware {
        val snap = workspaceRef(workspaceId).get()
        if (!snap.exists) null else snap.data(WorkspaceDoc.serializer()).ownerId
    }

    override suspend fun fetchMembers(workspaceId: String): List<RemoteMemberRef> = denyingAware {
        workspaceRef(workspaceId).collection(MEMBERS).get().documents.map { snap ->
            RemoteMemberRef(
                uid = snap.id,
                status = snap.data(WorkspaceMemberDoc.serializer()).status.toMemberStatus(),
            )
        }
    }

    override suspend fun deleteWorkspaceCollection(workspaceId: String, collectionName: String) =
        denyingAware { deleteAllDocs(workspaceRef(workspaceId).collection(collectionName)) }

    override suspend fun deleteWorkspace(workspaceId: String) = denyingAware {
        workspaceRef(workspaceId).delete()
    }

    override suspend fun markMemberLeft(workspaceId: String, uid: String, nowMillis: Long) = denyingAware {
        workspaceRef(workspaceId).collection(MEMBERS).document(uid).update(
            "status" to WorkspaceMemberStatus.LEFT.name,
            "leftAt" to nowMillis,
            "updatedAt" to nowMillis,
            "clientVersionCode" to CLIENT_VERSION_CODE,
        )
    }

    override suspend fun fetchEmailMappingUid(emailKey: String): String? = denyingAware {
        val snap = firestore.collection(USER_EMAILS).document(emailKey).get()
        if (!snap.exists) null else snap.data(UserEmailDoc.serializer()).uid
    }

    override suspend fun deleteEmailMapping(emailKey: String) = denyingAware {
        firestore.collection(USER_EMAILS).document(emailKey).delete()
    }

    override suspend fun deleteUserDoc(uid: String) = denyingAware { userRef(uid).delete() }

    private fun userRef(uid: String): DocumentReference =
        firestore.collection(USERS).document(uid)

    private fun workspaceRef(workspaceId: String): DocumentReference =
        firestore.collection(WORKSPACES).document(workspaceId)

    /** Firestore caps a write batch at 500 operations; chunk well below that. */
    private suspend fun deleteAllDocs(collection: CollectionReference) {
        collection.get().documents.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { snap -> batch.delete(snap.reference) }
            batch.commit()
        }
    }

    /** Unknown / absent statuses on legacy docs are treated as ACTIVE, matching firestore.rules. */
    private fun String.toMemberStatus(): WorkspaceMemberStatus =
        WorkspaceMemberStatus.entries.firstOrNull { it.name == this } ?: WorkspaceMemberStatus.ACTIVE

    /**
     * Translates Firestore's PERMISSION_DENIED into the SDK-free [RemoteAccessDeniedException] so
     * callers never import a Firebase type. Mirrors SyncErrorClassifier: gitlive exposes no
     * cross-platform code enum, so the message is what we can inspect.
     */
    private inline fun <T> denyingAware(block: () -> T): T =
        try {
            block()
        } catch (e: FirebaseFirestoreException) {
            if (e.message?.lowercase()?.contains("permission") == true) {
                throw RemoteAccessDeniedException(e.message, e)
            }
            throw e
        }

    private companion object {
        const val USERS = "users"
        const val USER_EMAILS = "userEmails"
        const val WORKSPACES = "workspaces"
        const val MEMBERS = "members"
        const val CLIENT_VERSION_CODE = 1
        const val MAX_BATCH_SIZE = 400
    }
}
