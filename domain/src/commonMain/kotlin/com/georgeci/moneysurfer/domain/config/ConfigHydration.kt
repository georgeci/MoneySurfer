package com.georgeci.moneysurfer.domain.config

/**
 * Warms the configuration engine's in-memory mirrors.
 *
 * Every backing store is suspend-only, so the synchronous reads on [HostCapabilities] need each
 * layer loaded once first. `AppLaunchViewModel` awaits this alongside the startup work it already
 * performs, before it resolves the start route — so nothing renders against a half-loaded chain.
 *
 * Exists as a domain facade because `navigation` (like feature modules) must not depend on
 * `app-config`. Idempotent; mirroring remote layers is what makes them survive a cold start
 * offline, not what makes reads synchronous.
 */
fun interface ConfigHydration {
    suspend fun hydrate()
}
