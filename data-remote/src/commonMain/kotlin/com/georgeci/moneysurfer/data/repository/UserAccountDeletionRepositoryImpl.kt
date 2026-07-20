package com.georgeci.moneysurfer.data.repository

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.remote.UserDoc
import com.georgeci.moneysurfer.data.remote.UserEmailDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceMemberDoc
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.repositories.UserAccountDeletionRepository
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import org.koin.core.annotation.Single
import kotlin.time.Clock

/**
 * Client-side Firestore cleanup for account deletion (issue #213). Every step is idempotent —
 * a retry after a part-way failure re-runs against whatever documents are left. The caller
 * ([DeleteUserAccountUseCase][com.georgeci.moneysurfer.domain.usecase.DeleteUserAccountUseCase])
 * guarantees sync is shut down first, so nothing re-creates docs behind the purge.
 *
 * Deletion semantics mirror the hosted account-deletion page: workspaces that are effectively
 * personal (owned, no other ACTIVE member) are hard-deleted; in workspaces shared with other
 * people the user's member row flips to LEFT and the content stays for the remaining members.
 */
@Single(binds = [UserAccountDeletionRepository::class])
class UserAccountDeletionRepositoryImpl(
    private val firestore: FirebaseFirestore,
) : UserAccountDeletionRepository {

    private val log = Logger.withTag(TAG)

    override suspend fun deleteRemoteUserData(
        uid: String,
        email: String?,
    ): Either<AccountDeletionError, Unit> = Either.catch {
        val userRef = firestore.collection("users").document(uid)
        val userSnap = userRef.get()
        val workspaceIds = if (userSnap.exists) {
            userSnap.data(UserDoc.serializer()).workspaceIds.distinct()
        } else {
            emptyList()
        }
        log.i { "[delete] start uid=${uid.redactUid()} workspaces=${workspaceIds.size}" }

        workspaceIds.forEach { workspaceId ->
            try {
                disposeWorkspace(workspaceId, uid)
            } catch (e: FirebaseFirestoreException) {
                // A stale ref in users/{uid}.workspaceIds (left / evicted earlier) reads as
                // PERMISSION_DENIED — there is nothing of the user's to clean up there.
                if (!e.isPermissionDenied()) throw e
                log.w { "[delete] no access to workspace=$workspaceId — skipping stale ref" }
            }
        }
        deleteEmailMapping(email, uid)
        userRef.delete()
        log.i { "[delete] remote data cleared uid=${uid.redactUid()}" }
    }.mapLeft { AccountDeletionError.RemoteDataCleanupFailed(cause = it) }

    private suspend fun disposeWorkspace(workspaceId: String, uid: String) {
        val workspaceRef = firestore.collection("workspaces").document(workspaceId)
        val workspaceSnap = workspaceRef.get()
        if (!workspaceSnap.exists) return
        val ownerId = workspaceSnap.data(WorkspaceDoc.serializer()).ownerId

        val memberSnaps = workspaceRef.collection("members").get().documents
        val hasOtherActiveMembers = memberSnaps.any { snap ->
            snap.id != uid && snap.data(WorkspaceMemberDoc.serializer()).status == STATUS_ACTIVE
        }

        if (ownerId == uid && !hasOtherActiveMembers) {
            log.i { "[delete] purging personal workspace=$workspaceId" }
            purgeWorkspace(workspaceRef)
        } else {
            log.i { "[delete] leaving shared workspace=$workspaceId" }
            leaveWorkspace(workspaceRef, uid)
        }
    }

    /**
     * Hard-delete of a personal workspace. Members go last among subcollections (listing the
     * others requires an ACTIVE membership row) and the root doc goes after everything —
     * the `isOwner()` rule reads it to authorize each delete.
     */
    private suspend fun purgeWorkspace(workspaceRef: DocumentReference) {
        ENTITY_COLLECTIONS.forEach { name -> deleteAllDocs(workspaceRef.collection(name)) }
        deleteAllDocs(workspaceRef.collection("members"))
        workspaceRef.delete()
    }

    /**
     * Marks the user's own member row LEFT instead of deleting it — profile snapshots stay
     * visible to remaining members, matching the retention promise on the hosted page. A row
     * the owner already flipped to REMOVED is left alone (rules deny self-updates on it).
     */
    private suspend fun leaveWorkspace(workspaceRef: DocumentReference, uid: String) {
        val memberRef = workspaceRef.collection("members").document(uid)
        val memberSnap = memberRef.get()
        if (!memberSnap.exists) return
        val status = memberSnap.data(WorkspaceMemberDoc.serializer()).status
        if (status != STATUS_ACTIVE) return
        val now = Clock.System.now().toEpochMilliseconds()
        memberRef.update(
            "status" to STATUS_LEFT,
            "leftAt" to now,
            "updatedAt" to now,
            "clientVersionCode" to CLIENT_VERSION_CODE,
        )
    }

    /** Removes the email→uid lookup row, but only when it still points at the deleted user. */
    private suspend fun deleteEmailMapping(email: String?, uid: String) {
        val key = email?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return
        val ref = firestore.collection("userEmails").document(key)
        val snap = ref.get()
        if (snap.exists && snap.data(UserEmailDoc.serializer()).uid == uid) {
            ref.delete()
        }
    }

    private suspend fun deleteAllDocs(collection: CollectionReference) {
        val docs = collection.get().documents
        docs.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { snap -> batch.delete(snap.reference) }
            batch.commit()
        }
    }

    /** Mirrors SyncErrorClassifier: gitlive has no cross-platform Code enum, so inspect the message. */
    private fun FirebaseFirestoreException.isPermissionDenied(): Boolean =
        message?.lowercase()?.contains("permission") == true

    private companion object {
        const val TAG = "AccountDeletion"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_LEFT = "LEFT"
        const val CLIENT_VERSION_CODE = 1

        /** Firestore caps a write batch at 500 operations; stay comfortably below. */
        const val MAX_BATCH_SIZE = 400

        /** Workspace subcollections purged before members + root doc. */
        val ENTITY_COLLECTIONS = listOf(
            "accounts",
            "categories",
            "transactions",
            "invites",
            "budgets",
            "recurringRules",
        )
    }
}
