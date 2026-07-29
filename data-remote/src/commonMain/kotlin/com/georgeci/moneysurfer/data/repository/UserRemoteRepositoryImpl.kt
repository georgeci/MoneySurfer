package com.georgeci.moneysurfer.data.repository

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.remote.UserDoc
import com.georgeci.moneysurfer.data.remote.UserEmailDoc
import com.georgeci.moneysurfer.data.remote.UserRemoteSource
import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import org.koin.core.annotation.Single

@Single(binds = [UserRemoteRepository::class])
class UserRemoteRepositoryImpl(
    private val remoteSource: UserRemoteSource,
    private val clock: ClockUseCase,
) : UserRemoteRepository {

    private val log = Logger.withTag(TAG)

    override suspend fun fetch(uid: String): User? {
        val doc = remoteSource.fetchUser(uid) ?: return null
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
        remoteSource.createUser(
            uid = uid,
            doc = UserDoc(
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
        remoteSource.addWorkspaceRef(uid, workspaceId.value)
    }

    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) {
        remoteSource.setDefaultWorkspace(uid, workspaceId.value)
    }

    override suspend fun findByEmail(email: String): UserId? {
        val key = email.emailKey()
        if (key.isBlank()) return null
        val doc = remoteSource.fetchEmailMapping(key)
        if (doc == null) {
            log.d { "[findByEmail] miss email=${key.redactEmail()}" }
            return null
        }
        return when {
            doc.uid.isBlank() -> {
                log.w { "[findByEmail] mapping email=${key.redactEmail()} has empty uid — ignoring" }
                null
            }
            else -> {
                log.i { "[findByEmail] hit email=${key.redactEmail()} uid=${doc.uid.redactUid()}" }
                UserId(doc.uid)
            }
        }
    }

    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        remoteSource.addInvitedWorkspaceRef(uid, workspaceId.value)
    }

    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        remoteSource.removeInvitedWorkspaceRef(uid, workspaceId.value)
    }

    override suspend fun upsertEmailMapping(email: String, uid: String) {
        val key = email.emailKey()
        if (key.isBlank() || uid.isBlank()) {
            log.w { "[email-map:skip] email='${email.redactEmail()}' uid='${uid.redactUid()}' — blank, skipping" }
            return
        }
        remoteSource.upsertEmailMapping(
            emailKey = key,
            doc = UserEmailDoc(uid = uid, updatedAt = clock.now().toEpochMilliseconds()),
        )
        log.i { "[email-map:upsert] email=${key.redactEmail()} uid=${uid.redactUid()}" }
    }

    private companion object {
        const val TAG = "UserRemote"
    }
}

/** The document id an email maps to. Lower-cased and trimmed so a lookup and a write agree. */
private fun String.emailKey(): String = trim().lowercase()
