package com.georgeci.moneysurfer.domain

/**
 * Single Koin-bound switch for the "offline" host (no Firebase / sync). Online
 * (`composeApp`) registers `isOffline = false`; offline (`composeAppOffline`)
 * registers `isOffline = true`. Features read this instead of probing
 * BuildConfig so the gating is testable and lives in one place.
 */
data class OfflineBuildFlags(
    val isOffline: Boolean,
)
