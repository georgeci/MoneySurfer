package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

/**
 * Participation of a [User] in a [Workspace].
 *
 * Snapshots ([displayName], [email]) are intentionally duplicated here so the membership
 * roster keeps stable history even after the user later edits their profile or deletes
 * their account. They are populated at the moment the row is written and never retro-updated.
 *
 * Soft-delete via [status] / [leftAt] / [removedAt] — see [WorkspaceMemberStatus].
 */
data class WorkspaceMember(
    val userId: UserId,
    val workspaceId: WorkspaceId,
    val role: WorkspaceRole,
    val status: WorkspaceMemberStatus = WorkspaceMemberStatus.ACTIVE,
    val displayName: String = "",
    val email: String? = null,
    val addedByUserId: UserId? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val leftAt: Instant? = null,
    val removedAt: Instant? = null,
)
