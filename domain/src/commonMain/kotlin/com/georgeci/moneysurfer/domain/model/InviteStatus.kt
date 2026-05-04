package com.georgeci.moneysurfer.domain.model

/**
 * Lifecycle of a [WorkspaceInvite].
 *
 * - [PENDING] — created by an owner; awaiting recipient action.
 * - [ACCEPTED] — recipient joined; a [WorkspaceMember] row exists for them.
 * - [DECLINED] — recipient declined; terminal.
 * - [CANCELLED] — owner revoked before response; terminal.
 * - [EXPIRED] — `now > expiresAt`. Computed client-side for display when status is still
 *   PENDING; only persisted if a flow explicitly transitions the doc.
 */
enum class InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED,
    EXPIRED,
}
