package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

fun workspaceInviteId(value: String = "inv-1"): WorkspaceInviteId = WorkspaceInviteId(value)

/**
 * Default `createdAt` uses `Clock.System.now()` (not the static `testInstant`) so the
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
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
    expiresAt: Instant = createdAt + 14.days,
    respondedAt: Instant? = null,
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
