package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first

/**
 * Validates the **upgraded** `workspace_members` schema (Phase 0):
 * snapshot fields (`displayName` / `email` / `addedByUserId`), soft-delete
 * timestamps (`leftAt` / `removedAt`), and the new `status` index round-trip
 * through real Room SQL.
 *
 * Catches schema drift the unit-level dual-write spec misses — the
 * [com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao] uses a list-backed
 * fake there, so wrong column names or missing indices only surface here.
 */
class WorkspaceMemberRepositoryIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness

    beforeEach {
        harness = IntegrationHarness()
        harness.database.userDao().insert(
            UserEntity(id = "owner-uid", displayName = "Owner", isAnon = false),
        )
        harness.database.userDao().insert(
            UserEntity(id = "member-uid", displayName = "Member", isAnon = false),
        )
        harness.database.workspaceDao().insert(
            WorkspaceEntity(
                id = "ws-1",
                name = "WS",
                description = "",
                baseCurrency = "USD",
                ownerId = "owner-uid",
                createdAt = NOW,
                archived = false,
                updatedAt = NOW,
            ),
        )
    }

    afterEach { harness.close() }

    "insert + getById round-trips snapshots and timestamps" {
        val dao = harness.database.workspaceMemberDao()
        dao.insert(activeMember(userId = "member-uid"))

        val row = dao.getById("member-uid", "ws-1")!!
        row.role shouldBe "EDITOR"
        row.status shouldBe "ACTIVE"
        row.displayName shouldBe "Member Snapshot"
        row.email shouldBe "member@example.com"
        row.addedByUserId shouldBe "owner-uid"
        row.createdAt shouldBe NOW
        row.leftAt shouldBe null
        row.removedAt shouldBe null
    }

    "soft-delete via upsert sets status REMOVED and stamps removedAt without erasing the row" {
        val dao = harness.database.workspaceMemberDao()
        dao.insert(activeMember(userId = "member-uid"))
        val current = dao.getById("member-uid", "ws-1")!!

        dao.upsert(current.copy(status = "REMOVED", removedAt = NOW + 500, updatedAt = NOW + 500))

        val row = dao.getById("member-uid", "ws-1")
        row shouldNotBe null
        row!!.status shouldBe "REMOVED"
        row.removedAt shouldBe NOW + 500
        // Snapshot preserved for history.
        row.email shouldBe "member@example.com"
    }

    "leftAt path keeps role unchanged and status flipped to LEFT" {
        val dao = harness.database.workspaceMemberDao()
        dao.insert(activeMember(userId = "member-uid"))
        val current = dao.getById("member-uid", "ws-1")!!

        dao.upsert(current.copy(status = "LEFT", leftAt = NOW + 700, updatedAt = NOW + 700))

        val row = dao.getById("member-uid", "ws-1")!!
        row.role shouldBe "EDITOR"
        row.status shouldBe "LEFT"
        row.leftAt shouldBe NOW + 700
    }

    "getByWorkspaceId returns ACTIVE and soft-deleted rows alike (UI filters by status)" {
        val dao = harness.database.workspaceMemberDao()
        dao.insert(activeMember(userId = "owner-uid", role = "OWNER"))
        dao.insert(activeMember(userId = "member-uid", role = "EDITOR"))
        val ex = dao.getById("member-uid", "ws-1")!!
        dao.upsert(ex.copy(status = "REMOVED", removedAt = NOW + 100))

        val rows = dao.getByWorkspaceId("ws-1").first().sortedBy { it.userId }
        rows.map { it.userId to it.status } shouldBe listOf(
            "member-uid" to "REMOVED",
            "owner-uid" to "ACTIVE",
        )
    }
})

private const val NOW: Long = 1_700_000_000_000L

private fun activeMember(
    userId: String,
    workspaceId: String = "ws-1",
    role: String = "EDITOR",
): WorkspaceMemberEntity = WorkspaceMemberEntity(
    userId = userId,
    workspaceId = workspaceId,
    role = role,
    status = "ACTIVE",
    displayName = "Member Snapshot",
    email = "member@example.com",
    addedByUserId = "owner-uid",
    createdAt = NOW,
    updatedAt = NOW,
    leftAt = null,
    removedAt = null,
)
