package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class SelectWorkspaceUseCaseTest : StringSpec({

    "writes local pointer and skips remote when no Firebase uid" {
        val remote = RecordingUserRemoteRepository()
        val session = InMemorySessionPointers(currentFirebaseUid = null)
        val useCase = SelectWorkspaceUseCase(session, session, remote)

        useCase(WORKSPACE_ID)

        session.currentWorkspaceId.first() shouldBe WORKSPACE_ID
        remote.setDefaultCalls shouldHaveSize 0
    }

    "writes local pointer and pushes default when Firebase uid present" {
        val remote = RecordingUserRemoteRepository()
        val session = InMemorySessionPointers(currentFirebaseUid = FIREBASE_UID)
        val useCase = SelectWorkspaceUseCase(session, session, remote)

        useCase(WORKSPACE_ID)

        session.currentWorkspaceId.first() shouldBe WORKSPACE_ID
        remote.setDefaultCalls shouldBe listOf(FIREBASE_UID to WORKSPACE_ID)
    }

    "local pointer survives a remote failure" {
        val remote = RecordingUserRemoteRepository(failOnSetDefault = true)
        val session = InMemorySessionPointers(currentFirebaseUid = FIREBASE_UID)
        val useCase = SelectWorkspaceUseCase(session, session, remote)

        useCase(WORKSPACE_ID)

        session.currentWorkspaceId.first() shouldBe WORKSPACE_ID
        remote.setDefaultCalls shouldHaveSize 0
    }
})

private val WORKSPACE_ID = WorkspaceId("ws-target")
private const val FIREBASE_UID = "firebase-uid-1"

private class RecordingUserRemoteRepository(
    private val failOnSetDefault: Boolean = false,
) : UserRemoteRepository {
    val setDefaultCalls = mutableListOf<Pair<String, WorkspaceId>>()
    override suspend fun fetch(uid: String): User? = null
    override suspend fun create(uid: String, displayName: String?, email: String?, isAnon: Boolean, createdAt: Long) =
        error("not used")
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error("not used")
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) {
        if (failOnSetDefault) throw RuntimeException("network down")
        setDefaultCalls += uid to workspaceId
    }
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}
