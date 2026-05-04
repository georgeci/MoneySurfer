package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId

/**
 * Email-based invitation to a [Workspace].
 *
 * Lives at `workspaces/{workspaceId}/invites/{id}` in Firestore. The invitee discovers
 * pending invites in-app via [email] match (no email delivery — see md/members.md decision 4).
 *
 * - [targetUserId] is populated lazily (null when first invited unknown email; resolved on
 *   accept). Faster lookup for return users.
 * - Expiration is checked client-side: status PENDING + `now > expiresAt` ⇒ display as EXPIRED.
 *   Persisted EXPIRED only on explicit transition.
 */
data class WorkspaceInvite(
    val id: WorkspaceInviteId,
    val workspaceId: WorkspaceId,
    val email: String,
    val targetUserId: UserId?,
    val role: WorkspaceRole,
    val status: InviteStatus,
    val invitedByUserId: UserId,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
    val respondedAt: Long? = null,
)
