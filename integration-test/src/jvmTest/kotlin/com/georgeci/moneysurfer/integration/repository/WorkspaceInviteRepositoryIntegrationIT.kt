package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * Exercises the **real** Room schema for `workspace_invites` end-to-end:
 *   - entity columns + composite indexes compile,
 *   - FK to `workspaces.id` is enforced,
 *   - status filters used by [com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao]
 *     resolve through actual SQL,
 *   - `getByEmail` / `getByTargetUserId` surface only matching rows.
 *
 * Unit tests use a list-backed fake DAO; this is the spec that catches schema
 * drift (wrong column name, missing index, bad FK target) before it ships.
 */
class WorkspaceInviteRepositoryIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness

    beforeEach {
        harness = IntegrationHarness()
        // FK parent: workspaces row required so invite rows can reference it.
        harness.database.userDao().insert(
            UserEntity(id = "owner-uid", displayName = "Owner", isAnon = false),
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

    "insert + getById round-trips all columns including snapshots" {
        val dao = harness.database.workspaceInviteDao()
        dao.insert(invite(id = "inv-1", email = "alice@example.com", status = "PENDING"))

        val row = dao.getById("inv-1")
        row shouldBe invite(id = "inv-1", email = "alice@example.com", status = "PENDING")
    }

    "getByWorkspaceId only returns invites of the given workspace" {
        val dao = harness.database.workspaceInviteDao()
        dao.insert(invite(id = "inv-1", workspaceId = "ws-1"))
        // Second workspace required to prove the filter — cannot insert with FK violation otherwise.
        harness.database.workspaceDao().insert(
            WorkspaceEntity(
                id = "ws-2",
                name = "Other",
                description = "",
                baseCurrency = "USD",
                ownerId = "owner-uid",
                createdAt = NOW,
                archived = false,
                updatedAt = NOW,
            ),
        )
        dao.insert(invite(id = "inv-2", workspaceId = "ws-2"))

        dao.getByWorkspaceId("ws-1").first().map { it.id } shouldBe listOf("inv-1")
    }

    "getByEmail / getByTargetUserId resolve through their indexes" {
        val dao = harness.database.workspaceInviteDao()
        dao.insert(invite(id = "inv-1", email = "alice@example.com", targetUserId = null))
        dao.insert(invite(id = "inv-2", email = "bob@example.com", targetUserId = "bob-uid"))

        dao.getByEmail("bob@example.com").first().map { it.id } shouldBe listOf("inv-2")
        dao.getByTargetUserId("bob-uid").first().map { it.id } shouldBe listOf("inv-2")
        dao.getByTargetUserId("ghost").first() shouldHaveSize 0
    }

    "upsert applies status transition and respondedAt without losing snapshot fields" {
        val dao = harness.database.workspaceInviteDao()
        dao.insert(invite(id = "inv-1"))

        val updated = dao.getById("inv-1")!!.copy(
            status = "ACCEPTED",
            respondedAt = NOW + 100,
            updatedAt = NOW + 100,
        )
        dao.upsert(updated)

        val row = dao.getById("inv-1")!!
        row.status shouldBe "ACCEPTED"
        row.respondedAt shouldBe NOW + 100
        row.email shouldBe "alice@example.com"
        row.invitedByUserId shouldBe "owner-uid"
    }

    "insert with unknown workspaceId is allowed (no FK on workspace_invites.workspaceId)" {
        // Recipients pull invites for workspaces they're NOT a member of yet, so the
        // parent workspace row may not exist locally when the invite is upserted from
        // a collection-group pull. The FK was dropped in DB v17 to fix SQLite error
        // 787 on accept. This test pins the new contract: unknown-parent inserts must
        // succeed, and the row is queryable afterwards.
        val dao = harness.database.workspaceInviteDao()
        dao.insert(invite(id = "inv-orphan", workspaceId = "ghost-workspace"))
        dao.getById("inv-orphan")?.workspaceId shouldBe "ghost-workspace"
    }
})

private const val NOW: Long = 1_700_000_000_000L
private const val EXPIRY: Long = NOW + 14L * 24L * 60L * 60L * 1000L

private fun invite(
    id: String,
    workspaceId: String = "ws-1",
    email: String = "alice@example.com",
    targetUserId: String? = null,
    status: String = "PENDING",
): WorkspaceInviteEntity = WorkspaceInviteEntity(
    id = id,
    workspaceId = workspaceId,
    email = email,
    targetUserId = targetUserId,
    role = "EDITOR",
    status = status,
    invitedByUserId = "owner-uid",
    createdAt = NOW,
    updatedAt = NOW,
    expiresAt = EXPIRY,
    respondedAt = null,
)
