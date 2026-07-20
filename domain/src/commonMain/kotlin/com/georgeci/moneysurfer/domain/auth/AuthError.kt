package com.georgeci.moneysurfer.domain.auth

/**
 * Single typed failure for the auth-flow `Either`s. The data layer classifies provider-specific
 * exceptions (e.g. FirebaseAuth subtypes) into [Type] at the boundary so domain use cases and the
 * UI never need to know about Firebase types.
 */
data class AuthError(
    val type: Type,
    val message: String? = null,
    val cause: Throwable? = null,
) {
    enum class Type {
        InvalidCredentials,
        EmailAlreadyInUse,
        WeakPassword,

        /**
         * Firestore rules denied a read or write that the bootstrap relies on
         * (typically `users/{uid}` or `workspaces/{wid}`). Hard failure — there
         * is no recovery the UI can drive other than telling the user to retry
         * after the rules / membership are corrected.
         */
        PermissionDenied,

        /**
         * The provider refused a destructive call (account deletion) because the session's
         * last sign-in is too old — a fresh re-authentication is required first.
         */
        RequiresRecentLogin,
        Unknown,
    }
}
