package com.georgeci.moneysurfer.domain.usecase

/**
 * Typed failures for invite + member-management use cases. UI maps each variant to a
 * user-facing message. Cause-bearing variants keep the underlying [Throwable] for logging.
 */
sealed interface InviteError {
    /** Session has no `currentUserId`. Caller should not reach the action. */
    data object NoCurrentUser : InviteError

    /** Operation requires OWNER role on the workspace; caller is not. */
    data object NotOwner : InviteError

    /** Email failed normalization (empty / no `@` / etc.). */
    data object InvalidEmail : InviteError

    /** Email is already an ACTIVE member of this workspace. */
    data class AlreadyMember(val email: String) : InviteError

    /** A PENDING invite for this email + workspace already exists. */
    data class AlreadyInvited(val email: String) : InviteError

    /** No user has registered with this email — invite cannot be addressed. */
    data class UserNotFound(val email: String) : InviteError

    /** Lookup by id/token returned no row. */
    data object InviteNotFound : InviteError

    /** Invite is past `expiresAt` and cannot be accepted/declined. */
    data object InviteExpired : InviteError

    /** Invite status is not PENDING — already accepted/declined/cancelled. */
    data object InviteNotPending : InviteError

    /** Last OWNER cannot leave or be removed; transfer ownership first (post-v1). */
    data object OwnerCannotLeave : InviteError

    /** Demoting the last OWNER would leave the workspace ownerless. */
    data object CannotDemoteLastOwner : InviteError

    /** Local Room write failed (FK violation, disk full, etc.). Action did not complete. */
    data class LocalWriteFailed(val cause: Throwable) : InviteError

    /** Best-effort remote sync failed; caller may retry on next sync cycle. */
    data class RemoteSyncFailed(val cause: Throwable) : InviteError
}
