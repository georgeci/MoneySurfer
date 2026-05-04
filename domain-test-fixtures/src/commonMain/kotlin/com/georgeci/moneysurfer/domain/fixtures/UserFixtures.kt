package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

fun aUser(
    id: UserId = userId(),
    displayName: String? = "Test User",
    email: String? = "test@example.com",
    isAnon: Boolean = false,
    workspaceIds: List<WorkspaceId> = emptyList(),
    defaultWorkspaceId: WorkspaceId? = null,
): User = User(
    id = id,
    displayName = displayName,
    email = email,
    isAnon = isAnon,
    workspaceIds = workspaceIds,
    defaultWorkspaceId = defaultWorkspaceId,
)

/** Demo / not-yet-linked-to-Firebase user. */
fun anAnonymousUser(
    id: UserId = userId("u-anon"),
): User = aUser(id = id, displayName = null, email = null, isAnon = true)
