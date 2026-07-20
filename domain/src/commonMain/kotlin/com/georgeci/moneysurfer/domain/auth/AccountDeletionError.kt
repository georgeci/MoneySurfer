package com.georgeci.moneysurfer.domain.auth

/**
 * Typed failure for the user-account deletion flow (issue #213). Ordering matters to the UI:
 * everything up to and including [RemoteDataCleanupFailed] leaves the account intact and fully
 * retryable; [AuthDeletionFailed] means the Firestore data is already gone and only the
 * Firebase Auth user remains, so the retry repeats a now-idempotent cleanup and the auth delete.
 */
sealed interface AccountDeletionError {

    /** No active session — nothing to delete. */
    data object NotSignedIn : AccountDeletionError

    /** Password re-authentication was rejected before anything was touched. */
    data object InvalidCredentials : AccountDeletionError

    /** Re-authentication failed for a non-credential reason (e.g. network); nothing was touched. */
    data class ReauthenticationFailed(val cause: Throwable? = null) : AccountDeletionError

    /**
     * Firebase refused the destructive call because the session's last sign-in is too old
     * and no password was available to refresh it (anonymous sessions cannot re-auth).
     */
    data object RequiresRecentLogin : AccountDeletionError

    /**
     * Firestore cleanup failed part-way. Safe to retry: the deletion steps are idempotent
     * and the remaining documents are still owned by the (still existing) auth user.
     */
    data class RemoteDataCleanupFailed(val cause: Throwable? = null) : AccountDeletionError

    /** Firestore data is gone but the Firebase Auth user could not be deleted. */
    data class AuthDeletionFailed(val cause: Throwable? = null) : AccountDeletionError
}
