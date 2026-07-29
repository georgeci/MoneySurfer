package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.data.remote.WorkspaceInviteDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceMemberDoc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val WORKSPACE = "ws-1"

private fun memberEntity() = WorkspaceMemberEntity(
    userId = "u-2",
    workspaceId = WORKSPACE,
    role = "EDITOR",
    status = "ACTIVE",
    displayName = "Ada",
    email = "ada@example.com",
    addedByUserId = "u-1",
    createdAt = 1L,
    updatedAt = 2L,
    leftAt = null,
    removedAt = null,
)

private fun inviteEntity() = WorkspaceInviteEntity(
    id = "inv-1",
    workspaceId = WORKSPACE,
    email = "ada@example.com",
    targetUserId = "u-2",
    role = "EDITOR",
    status = "PENDING",
    invitedByUserId = "u-1",
    createdAt = 1L,
    updatedAt = 2L,
    expiresAt = 99L,
    respondedAt = null,
)

/**
 * Membership and invite mappers. Both carry a lifecycle in a string field, and both have to read
 * documents written by clients that predate that field — an empty status must land on the value
 * the rest of the app treats as "still here" rather than on an empty string no branch matches.
 */
class MembershipDtoMapperSpec : StringSpec({

    "a member survives the round trip; the workspace comes from the path, not the doc" {
        memberEntity().toDoc().toEntity(workspaceId = WORKSPACE) shouldBe memberEntity()
    }

    "a member who left keeps both the status and the moment" {
        val left = memberEntity().copy(status = "LEFT", leftAt = 7L)

        left.toDoc().toEntity(workspaceId = WORKSPACE) shouldBe left
    }

    "a member who was removed keeps both the status and the moment" {
        val removed = memberEntity().copy(status = "REMOVED", removedAt = 8L)

        removed.toDoc().toEntity(workspaceId = WORKSPACE) shouldBe removed
    }

    "a member doc with no status is read as active rather than as a blank" {
        val doc = WorkspaceMemberDoc(userId = "u-2", role = "EDITOR", status = "", createdAt = 1L)

        doc.toEntity(workspaceId = WORKSPACE).status shouldBe "ACTIVE"
    }

    // The invite id the rules check against lives on the local row, not on the member doc the
    // mapper produces — WorkspaceMemberSyncPlugin stamps it at push time.
    "the mapper leaves the invite id off the member doc" {
        memberEntity().toDoc().inviteId shouldBe null
    }

    "an invite survives the round trip, expiry included" {
        inviteEntity().toDoc().toEntity(id = "inv-1", workspaceId = WORKSPACE) shouldBe
            inviteEntity()
    }

    "an answered invite keeps its status and the moment it was answered" {
        val accepted = inviteEntity().copy(status = "ACCEPTED", respondedAt = 42L)

        accepted.toDoc().toEntity(id = "inv-1", workspaceId = WORKSPACE) shouldBe accepted
    }

    "an invite doc with no status is read as pending rather than as a blank" {
        val doc = WorkspaceInviteDoc(email = "ada@example.com", role = "EDITOR", status = "")

        doc.toEntity(id = "inv-1", workspaceId = WORKSPACE).status shouldBe "PENDING"
    }

    "an invite with no resolved recipient maps without one" {
        val open = inviteEntity().copy(targetUserId = null)

        open.toDoc().targetUserId shouldBe null
        open.toDoc().toEntity(id = "inv-1", workspaceId = WORKSPACE).targetUserId shouldBe null
    }
})
