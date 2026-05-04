package com.georgeci.moneysurfer.domain.model

/**
 * Lifecycle of a [WorkspaceMember] row.
 *
 * Soft-delete only — Firestore rules forbid hard deletes, so a row's terminal state
 * is encoded in `status` + a matching `*At` timestamp. Pending invitees do NOT have
 * a member row; pending state lives on `WorkspaceInvite.status = PENDING` instead.
 *
 * - [ACTIVE] — current participant.
 * - [LEFT] — voluntarily left via `LeaveWorkspaceUseCase`. `WorkspaceMember.leftAt` set.
 * - [REMOVED] — kicked by an owner via `RemoveMemberUseCase`. `WorkspaceMember.removedAt` set.
 */
enum class WorkspaceMemberStatus {
    ACTIVE,
    LEFT,
    REMOVED,
}
