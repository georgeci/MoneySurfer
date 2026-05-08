package com.georgeci.moneysurfer.domain.firstrun

/**
 * Host-owned bootstrap that runs once at app launch, after the user is known but before the
 * workspace selector is shown. Online builds bind a no-op (Firestore-backed onboarding handles
 * empty workspaces); the offline build seeds a "Personal" workspace, default categories, and a
 * "Cash" account so a fresh install lands on a populated Dashboard instead of an empty selector.
 *
 * Implementations must be idempotent — relaunch must not duplicate workspace/account/category
 * rows. The session pointer flipping `currentWorkspaceId` from null → set is the durable
 * "first-run completed" flag (no separate boolean needed).
 */
fun interface FirstRunSeeder {
    suspend fun seedIfNeeded()
}
