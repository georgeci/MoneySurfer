package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.data.remote.WorkspaceDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceInviteDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceMemberDoc
import com.georgeci.moneysurfer.data.sync.WorkspaceRefRegistrar
import com.georgeci.moneysurfer.data.sync.plugin.WorkspaceInviteSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.WorkspaceMemberSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.WorkspaceSyncPlugin
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.inMemoryRoomDatabase
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val MEMBER = "u-2"
private const val INVITE = "inv-1"
private const val MEMBER_EMAIL = "ada@example.com"

/** Records the `users/{uid}.workspaceIds` writes the registrar makes; everything else is unused. */
private class RecordingUserRemoteRepository : UserRemoteRepository {

    val registeredWorkspaces: MutableList<String> = mutableListOf()

    override suspend fun fetch(uid: String): User? = null

    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = Unit

    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        registeredWorkspaces += workspaceId.value
    }

    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = Unit

    override suspend fun findByEmail(email: String): UserId? = null

    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit

    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit

    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
}

private fun memberEntity(updatedAt: Long, displayName: String = "Local name") =
    WorkspaceMemberEntity(
        userId = MEMBER,
        workspaceId = PLUGIN_WORKSPACE_ID,
        role = "EDITOR",
        status = "ACTIVE",
        displayName = displayName,
        email = MEMBER_EMAIL,
        createdAt = 1L,
        updatedAt = updatedAt,
    )

private fun memberDoc(updatedAt: Long = 200L, deletedAt: Long? = null) = WorkspaceMemberDoc(
    userId = MEMBER,
    role = "EDITOR",
    status = "ACTIVE",
    displayName = "Remote name",
    email = MEMBER_EMAIL,
    createdAt = 1L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun inviteEntity(updatedAt: Long, status: String = "PENDING") = WorkspaceInviteEntity(
    id = INVITE,
    workspaceId = PLUGIN_WORKSPACE_ID,
    email = MEMBER_EMAIL,
    targetUserId = MEMBER,
    role = "EDITOR",
    status = status,
    invitedByUserId = PLUGIN_OWNER_ID,
    createdAt = 1L,
    updatedAt = updatedAt,
    expiresAt = 9_999_999L,
)

private fun inviteDoc(updatedAt: Long = 200L, deletedAt: Long? = null) = WorkspaceInviteDoc(
    email = MEMBER_EMAIL,
    targetUserId = MEMBER,
    role = "EDITOR",
    status = "PENDING",
    invitedByUserId = PLUGIN_OWNER_ID,
    createdAt = 1L,
    updatedAt = updatedAt,
    expiresAt = 9_999_999L,
    deletedAt = deletedAt,
)

/**
 * The workspace root document and the two membership collections hanging off it.
 *
 * These three carry the rules-facing details the rest of the plugins do not: the workspace push
 * also registers the ref that makes the workspace visible on other devices at all (issue #342),
 * and a member self-create has to name the invite that admits it or Firestore rejects the write
 * (issue #152).
 */
class WorkspaceTreeSyncPluginIT : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var writer: RecordingDocumentWriter
    lateinit var userRemote: RecordingUserRemoteRepository
    lateinit var workspaces: WorkspaceSyncPlugin
    lateinit var members: WorkspaceMemberSyncPlugin
    lateinit var invites: WorkspaceInviteSyncPlugin

    beforeEach {
        database = inMemoryRoomDatabase()
        writer = RecordingDocumentWriter()
        userRemote = RecordingUserRemoteRepository()
        workspaces = WorkspaceSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            workspaceDao = database.workspaceDao(),
            userDao = database.userDao(),
            workspaceRefRegistrar = WorkspaceRefRegistrar(
                userRemoteRepository = userRemote,
                session = InMemorySessionPointers(currentFirebaseUid = PLUGIN_OWNER_ID),
            ),
        )
        members = WorkspaceMemberSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            workspaceMemberDao = database.workspaceMemberDao(),
            workspaceInviteDao = database.workspaceInviteDao(),
            userDao = database.userDao(),
        )
        invites = WorkspaceInviteSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            workspaceInviteDao = database.workspaceInviteDao(),
        )
        database.seedPluginWorkspace()
        database.userDao().insert(
            UserEntity(
                id = MEMBER,
                displayName = "Ada",
                isAnon = false,
            ),
        )
    }

    afterEach { database.close() }

    // ── workspace root ────────────────────────────────────────────────────────

    "a workspace is pushed to its own document, not a sub-collection" {
        workspaces.push(
            mutationOf(SyncEntityTypes.WORKSPACE, PLUGIN_WORKSPACE_ID, MutationOperation.INSERT),
        )

        writer.writes.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID"
        writer.onlyWrite<WorkspaceDoc>().name shouldBe "WS"
    }

    // Without the ref, `SyncScope.AllUserData` cannot find the workspace: it is invisible on every
    // other device and after any reinstall, permanently.
    "pushing a workspace registers its ref on the user document" {
        workspaces.push(
            mutationOf(SyncEntityTypes.WORKSPACE, PLUGIN_WORKSPACE_ID, MutationOperation.INSERT),
        )

        userRemote.registeredWorkspaces shouldContainExactly listOf(PLUGIN_WORKSPACE_ID)
    }

    "a workspace that is no longer in Room is neither pushed nor registered" {
        workspaces.push(
            mutationOf(SyncEntityTypes.WORKSPACE, "ws-gone", MutationOperation.INSERT),
        )

        writer.writes shouldContainExactly emptyList()
        userRemote.registeredWorkspaces shouldContainExactly emptyList()
    }

    "a pulled workspace stubs its owner so the foreign key holds on a fresh device" {
        val doc = WorkspaceDoc(name = "Shared", ownerId = "u-unknown", updatedAt = 5L)

        workspaces.applyDoc(StubRemoteDocument("ws-2", doc), "ws-2") shouldBe
            EntityApplyResult(applied = true, wasConflict = false)

        database.workspaceDao().getById("ws-2")?.name shouldBe "Shared"
        database.userDao().getById("u-unknown")?.id shouldBe "u-unknown"
    }

    // The sync layer owns the cleanup; dropping the row here would take its accounts and
    // transactions with it through the cascade.
    "a tombstoned workspace is left in Room rather than dropped" {
        val doc = WorkspaceDoc(name = "WS", ownerId = PLUGIN_OWNER_ID, deletedAt = 400L)

        workspaces.applyDoc(
            StubRemoteDocument(PLUGIN_WORKSPACE_ID, doc),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = false, wasConflict = false)

        database.workspaceDao().getById(PLUGIN_WORKSPACE_ID)?.name shouldBe "WS"
    }

    // ── members ───────────────────────────────────────────────────────────────

    "a member push stamps the invite that admits them" {
        database.workspaceInviteDao().insert(inviteEntity(updatedAt = 1L))
        database.workspaceMemberDao().upsert(memberEntity(updatedAt = 100L))

        members.push(
            mutationOf(SyncEntityTypes.WORKSPACE_MEMBER, MEMBER, MutationOperation.INSERT),
        )

        writer.writes.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID/members/$MEMBER"
        writer.onlyWrite<WorkspaceMemberDoc>().inviteId shouldBe INVITE
    }

    // Owner-created rows have no invite to point at, and the rules' owner branch never asks.
    "a member with no joinable invite is pushed without one" {
        database.workspaceMemberDao().upsert(memberEntity(updatedAt = 100L))

        members.push(
            mutationOf(SyncEntityTypes.WORKSPACE_MEMBER, MEMBER, MutationOperation.UPDATE),
        )

        writer.onlyWrite<WorkspaceMemberDoc>().inviteId.shouldBeNull()
    }

    "a pulled member is inserted and their user row stubbed" {
        members.applyDoc(StubRemoteDocument(MEMBER, memberDoc()), PLUGIN_WORKSPACE_ID) shouldBe
            EntityApplyResult(applied = true, wasConflict = false)

        database.workspaceMemberDao()
            .getById(userId = MEMBER, workspaceId = PLUGIN_WORKSPACE_ID)
            ?.displayName shouldBe "Remote name"
    }

    "a member tombstone removes the row" {
        database.workspaceMemberDao().upsert(memberEntity(updatedAt = 100L))

        members.applyDoc(
            StubRemoteDocument(MEMBER, memberDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)

        database.workspaceMemberDao()
            .getById(userId = MEMBER, workspaceId = PLUGIN_WORKSPACE_ID)
            .shouldBeNull()
    }

    // ── invites ───────────────────────────────────────────────────────────────

    "an invite is pushed to the workspace's invites collection" {
        database.workspaceInviteDao().insert(inviteEntity(updatedAt = 100L))

        invites.push(mutationOf(SyncEntityTypes.WORKSPACE_INVITE, INVITE, MutationOperation.INSERT))

        writer.writes.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID/invites/$INVITE"
        writer.onlyWrite<WorkspaceInviteDoc>().email shouldBe MEMBER_EMAIL
    }

    "a pulled invite is inserted, and a newer local answer is kept" {
        invites.applyDoc(StubRemoteDocument(INVITE, inviteDoc()), PLUGIN_WORKSPACE_ID) shouldBe
            EntityApplyResult(applied = true, wasConflict = false)

        database.workspaceInviteDao().upsert(inviteEntity(updatedAt = 500L, status = "ACCEPTED"))

        invites.applyDoc(
            StubRemoteDocument(INVITE, inviteDoc(updatedAt = 300L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = false, wasConflict = true)
        database.workspaceInviteDao().getById(INVITE)?.status shouldBe "ACCEPTED"
    }

    "an invite tombstone removes the row" {
        database.workspaceInviteDao().insert(inviteEntity(updatedAt = 100L))

        invites.applyDoc(
            StubRemoteDocument(INVITE, inviteDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)

        database.workspaceInviteDao().getById(INVITE).shouldBeNull()
    }
})
