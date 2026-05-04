package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.primitives.currentTimeMillis

fun workspaceInviteId(value: String = "inv-1"): WorkspaceInviteId = WorkspaceInviteId(value)

/**
 * Default `createdAt` uses `currentTimeMillis()` (not the static `TEST_EPOCH_MILLIS`) so the
 * 14-day expiry window stays in the future regardless of when the suite runs. Tests that need
 * a deterministic timestamp should pass `createdAt` explicitly.
 */
fun aWorkspaceInvite(
    id: WorkspaceInviteId = workspaceInviteId(),
    workspaceId: WorkspaceId = workspaceId(),
    email: String = "invitee@example.com",
    targetUserId: UserId? = null,
    role: WorkspaceRole = WorkspaceRole.EDITOR,
    status: InviteStatus = InviteStatus.PENDING,
    invitedByUserId: UserId = userId("owner-uid"),
    createdAt: Long = currentTimeMillis(),
    updatedAt: Long = createdAt,
    expiresAt: Long = createdAt + 14L * 24L * 60L * 60L * 1000L,
    respondedAt: Long? = null,
): WorkspaceInvite = WorkspaceInvite(
    id = id,
    workspaceId = workspaceId,
    email = email,
    targetUserId = targetUserId,
    role = role,
    status = status,
    invitedByUserId = invitedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    respondedAt = respondedAt,
)
