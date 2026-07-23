package com.georgeci.moneysurfer.domain.firstrun

/**
 * Host-owned bootstrap for the local database. Online builds bind a no-op (Firestore-backed
 * onboarding handles empty workspaces); the offline build seeds a local user, a "Personal"
 * workspace and the default categories, so the first-run account screen has somewhere to put
 * the account the user is about to create.
 *
 * Called from `OnboardingViewModel` when the user leaves the onboarding, and again from
 * `AppLaunchViewModel` on every later launch as a repair pass.
 *
 * Implementations must be idempotent — relaunch must not duplicate workspace/category rows. The
 * session pointer flipping `currentWorkspaceId` from null → set is the durable "seeded" flag
 * (no separate boolean needed).
 */
fun interface FirstRunSeeder {
    suspend fun seedIfNeeded()
}
