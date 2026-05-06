package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

fun aWorkspace(
    id: WorkspaceId = workspaceId(),
    name: String = "Personal",
    description: String = "",
    baseCurrency: CurrencyCode = USD,
    ownerId: UserId = userId(),
    createdAt: Instant = testInstant,
    archived: Boolean = false,
): Workspace = Workspace(
    id = id,
    name = name,
    description = description,
    baseCurrency = baseCurrency,
    ownerId = ownerId,
    createdAt = createdAt,
    archived = archived,
)

fun aWorkspaceMember(
    userId: UserId = userId(),
    workspaceId: WorkspaceId = workspaceId(),
    role: WorkspaceRole = WorkspaceRole.OWNER,
    status: WorkspaceMemberStatus = WorkspaceMemberStatus.ACTIVE,
    displayName: String = "Test User",
    email: String? = "test@example.com",
    addedByUserId: UserId? = null,
    createdAt: Instant = testInstant,
    updatedAt: Instant = createdAt,
    leftAt: Instant? = null,
    removedAt: Instant? = null,
): WorkspaceMember = WorkspaceMember(
    userId = userId,
    workspaceId = workspaceId,
    role = role,
    status = status,
    displayName = displayName,
    email = email,
    addedByUserId = addedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    leftAt = leftAt,
    removedAt = removedAt,
)
