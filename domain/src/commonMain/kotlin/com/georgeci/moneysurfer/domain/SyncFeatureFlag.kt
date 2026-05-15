package com.georgeci.moneysurfer.domain

/**
 * Runtime toggle for the sync feature: gates the Settings → Sync screen, the manual
 * "Force sync" action, the in-process periodic background ticker, and every
 * use-case-driven sync trigger (post-auth bootstrap, create-workspace push,
 * accept-invite hydration, incoming-invite refresh).
 *
 * Bound by the host module (online: `OnlineSignInModule`; offline:
 * `offlineSignInModule`) — same pattern as [OfflineBuildFlags], keeps the gating
 * testable and in one place instead of probing `BuildConfig` from features.
 */
data class SyncFeatureFlag(
    val enabled: Boolean,
)
