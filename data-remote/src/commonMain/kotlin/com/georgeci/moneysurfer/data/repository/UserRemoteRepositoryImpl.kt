package com.georgeci.moneysurfer.data.repository

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.remote.UserDoc
import com.georgeci.moneysurfer.data.remote.UserEmailDoc
import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single(binds = [UserRemoteRepository::class])
class UserRemoteRepositoryImpl(
    private val firestore: FirebaseFirestore,
) : UserRemoteRepository {

    private val log = Logger.withTag(TAG)

    override suspend fun fetch(uid: String): User? {
        val snap = firestore.collection("users").document(uid).get()
        if (!snap.exists) return null
        val doc = snap.data(UserDoc.serializer())
        return User(
            id = UserId(uid),
            displayName = doc.displayName,
            email = doc.email,
            isAnon = doc.isAnon,
            workspaceIds = doc.workspaceIds.map(::WorkspaceId),
            defaultWorkspaceId = doc.defaultWorkspaceId?.let(::WorkspaceId),
            invitedWorkspaceIds = doc.invitedWorkspaceIds.map(::WorkspaceId),
        )
    }

    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) {
        firestore.collection("users").document(uid).set(
            UserDoc(
                displayName = displayName,
                email = email,
                isAnon = isAnon,
                createdAt = createdAt,
                workspaceIds = emptyList(),
                defaultWorkspaceId = null,
            ),
        )
    }

    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        firestore.collection("users").document(uid).update(
            "workspaceIds" to FieldValue.arrayUnion(workspaceId.value),
        )
    }

    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) {
        firestore.collection("users").document(uid).update(
            "defaultWorkspaceId" to workspaceId.value,
        )
    }

    override suspend fun findByEmail(email: String): UserId? {
        val key = email.trim().lowercase()
        if (key.isBlank()) return null
        val snap = firestore.collection("userEmails").document(key).get()
        if (!snap.exists) {
            log.d { "[findByEmail] miss email=${key.redactEmail()}" }
            return null
        }
        val uid = snap.data(UserEmailDoc.serializer()).uid
        return when {
            uid.isBlank() -> {
                log.w { "[findByEmail] mapping email=${key.redactEmail()} has empty uid — ignoring" }
                null
            }
            else -> {
                log.i { "[findByEmail] hit email=${key.redactEmail()} uid=${uid.redactUid()}" }
                UserId(uid)
            }
        }
    }

    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        firestore.collection("users").document(uid).update(
            "invitedWorkspaceIds" to FieldValue.arrayUnion(workspaceId.value),
        )
    }

    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        firestore.collection("users").document(uid).update(
            "invitedWorkspaceIds" to FieldValue.arrayRemove(workspaceId.value),
        )
    }

    override suspend fun upsertEmailMapping(email: String, uid: String) {
        val key = email.trim().lowercase()
        if (key.isBlank() || uid.isBlank()) {
            log.w { "[email-map:skip] email='${email.redactEmail()}' uid='${uid.redactUid()}' — blank, skipping" }
            return
        }
        val now = Clock.System.now().toEpochMilliseconds()
        firestore.collection("userEmails").document(key).set(
            UserEmailDoc(uid = uid, updatedAt = now),
        )
        log.i { "[email-map:upsert] email=${key.redactEmail()} uid=${uid.redactUid()}" }
    }

    private companion object {
        const val TAG = "UserRemote"
    }
}
