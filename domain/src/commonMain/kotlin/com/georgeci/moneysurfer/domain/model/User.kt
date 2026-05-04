package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

/**
 * Canonical user record. Shared between local (Room) and remote (Firestore) sources.
 *
 * - Local Room storage only persists [id], [displayName], [isAnon]. The other fields default to
 *   empty/null on read because the local row is a cache + FK target, not the source of truth.
 * - The Firestore `users/{uid}` document round-trips every field. [workspaceIds] in particular
 *   is what the post-auth bootstrap walks to pull the user's workspaces on a fresh device.
 */
data class User(
    val id: UserId,
    val displayName: String?,
    val email: String?,
    val isAnon: Boolean,
    val workspaceIds: List<WorkspaceId> = emptyList(),
    val defaultWorkspaceId: WorkspaceId? = null,
    val invitedWorkspaceIds: List<WorkspaceId> = emptyList(),
)
