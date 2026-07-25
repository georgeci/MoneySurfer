package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Issue #342: a workspace created while `SyncFeatureFlag` was off has no
 * `users/{uid}.workspaceIds` entry, and `addWorkspaceRef` is only ever called at creation /
 * join time. Once the flag is flipped the outbox pushes the document, so the ref has to be
 * registered there or the workspace stays invisible to every other device forever.
 */
class WorkspaceRefRegistrarSpec : StringSpec({

    "registers the pushed workspace under the signed-in user" {
        val remote = RecordingUserRemote()
        val registrar = WorkspaceRefRegistrar(remote, InMemorySessionPointers(currentFirebaseUid = UID))

        registrar.register("ws-1")

        remote.addRefCalls shouldContainExactly listOf(UID to WorkspaceId("ws-1"))
    }

    "does nothing without a Firebase session" {
        // Demo / offline session: there is no remote user document to update.
        val remote = RecordingUserRemote()
        val registrar = WorkspaceRefRegistrar(remote, InMemorySessionPointers(currentFirebaseUid = null))

        registrar.register("ws-1")

        remote.addRefCalls.shouldBeEmpty()
    }

    "propagates a failure so the outbox retries the push" {
        // Swallowing here would leave the document on Firestore with no ref pointing at it —
        // exactly the state this class exists to prevent, and nothing else would notice.
        val remote = RecordingUserRemote(failOnAddRef = true)
        val registrar = WorkspaceRefRegistrar(remote, InMemorySessionPointers(currentFirebaseUid = UID))

        shouldThrow<IllegalStateException> { registrar.register("ws-1") }

        remote.addRefCalls.shouldBeEmpty()
    }

    "re-registering an already listed workspace is harmless" {
        // addWorkspaceRef is an arrayUnion, so the ordinary create path and this one can both
        // fire for the same workspace without producing a duplicate.
        val remote = RecordingUserRemote()
        val registrar = WorkspaceRefRegistrar(remote, InMemorySessionPointers(currentFirebaseUid = UID))

        registrar.register("ws-1")
        registrar.register("ws-1")

        remote.addRefCalls.size shouldBe 2
        remote.union shouldContainExactly listOf(WorkspaceId("ws-1"))
    }
})

private const val UID = "uid-342"
private const val UNUSED = "collaborator not exercised by this spec"

private class RecordingUserRemote(
    private val failOnAddRef: Boolean = false,
) : UserRemoteRepository {
    val addRefCalls = mutableListOf<Pair<String, WorkspaceId>>()

    /** Stands in for the server-side `arrayUnion` semantics. */
    val union = mutableListOf<WorkspaceId>()

    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        if (failOnAddRef) error("network down")
        addRefCalls += uid to workspaceId
        if (workspaceId !in union) union += workspaceId
    }

    override suspend fun fetch(uid: String): User? = null
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error(UNUSED)
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun findByEmail(email: String): UserId? = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}
