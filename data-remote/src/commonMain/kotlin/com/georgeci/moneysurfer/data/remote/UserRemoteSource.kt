package com.georgeci.moneysurfer.data.remote

import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single

/**
 * The `users/{uid}` and `userEmails/{emailKey}` documents, expressed without any Firebase type.
 *
 * Same seam as [AccountDeletionRemoteSource] and [AppConfigRemoteSource], for the same reason: the
 * parts of [UserRemoteRepositoryImpl][com.georgeci.moneysurfer.data.repository.UserRemoteRepositoryImpl]
 * worth testing are the email normalization, the blank-input guards and the doc→domain mapping,
 * and gitlive's client cannot be constructed off Android.
 *
 * The workspace-ref writers are field-level array mutations rather than whole-document writes: two
 * devices joining a workspace at once must not overwrite each other's entry.
 */
interface UserRemoteSource {

    /** `users/{uid}`, or null when the document does not exist. */
    suspend fun fetchUser(uid: String): UserDoc?

    suspend fun createUser(uid: String, doc: UserDoc)

    suspend fun addWorkspaceRef(uid: String, workspaceId: String)

    suspend fun setDefaultWorkspace(uid: String, workspaceId: String)

    suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: String)

    suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: String)

    /** `userEmails/{emailKey}`, or null when there is no mapping. */
    suspend fun fetchEmailMapping(emailKey: String): UserEmailDoc?

    suspend fun upsertEmailMapping(emailKey: String, doc: UserEmailDoc)
}

@Single(binds = [UserRemoteSource::class])
class FirebaseUserRemoteSource(
    private val firestore: FirebaseFirestore,
) : UserRemoteSource {

    override suspend fun fetchUser(uid: String): UserDoc? {
        val snapshot = firestore.collection(USERS).document(uid).get()
        return if (snapshot.exists) snapshot.data(UserDoc.serializer()) else null
    }

    override suspend fun createUser(uid: String, doc: UserDoc) {
        firestore.collection(USERS).document(uid).set(UserDoc.serializer(), doc)
    }

    override suspend fun addWorkspaceRef(uid: String, workspaceId: String) {
        firestore.collection(USERS).document(uid).update(
            "workspaceIds" to FieldValue.arrayUnion(workspaceId),
        )
    }

    override suspend fun setDefaultWorkspace(uid: String, workspaceId: String) {
        firestore.collection(USERS).document(uid).update(
            "defaultWorkspaceId" to workspaceId,
        )
    }

    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: String) {
        firestore.collection(USERS).document(uid).update(
            "invitedWorkspaceIds" to FieldValue.arrayUnion(workspaceId),
        )
    }

    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: String) {
        firestore.collection(USERS).document(uid).update(
            "invitedWorkspaceIds" to FieldValue.arrayRemove(workspaceId),
        )
    }

    override suspend fun fetchEmailMapping(emailKey: String): UserEmailDoc? {
        val snapshot = firestore.collection(USER_EMAILS).document(emailKey).get()
        return if (snapshot.exists) snapshot.data(UserEmailDoc.serializer()) else null
    }

    override suspend fun upsertEmailMapping(emailKey: String, doc: UserEmailDoc) {
        firestore.collection(USER_EMAILS).document(emailKey).set(UserEmailDoc.serializer(), doc)
    }

    private companion object {
        const val USERS = "users"
        const val USER_EMAILS = "userEmails"
    }
}
