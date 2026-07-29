package com.georgeci.moneysurfer.domain.config

/**
 * What this build of the app can do — the facts the host decides at compile time.
 *
 * Replaces `OfflineBuildFlags`, `SignInFeatureConfig` and `TransactionCreationFeatureConfig`: one
 * facade over the configuration engine's Build layer instead of three data classes with a Koin
 * binding each in every host.
 *
 * Reads are synchronous because the Build layer is an in-memory map, but they still go through
 * `Config`, so a QA override can shadow one locally. That makes them valid only **after**
 * [ConfigHydration.hydrate] has completed — which `AppLaunchViewModel` awaits before it resolves
 * the start route, i.e. before any screen that injects this facade can exist.
 */
interface HostCapabilities {

    /** Firebase-free build: no profile, sync, members, invites, logout or account deletion. */
    val isOffline: Boolean

    val signInEmailPassword: Boolean
    val signInAnonymous: Boolean
    val signInDemo: Boolean

    /** Multi-account transfers in transaction creation. */
    val transferEnabled: Boolean

    /** The per-widget card-style picker on the dashboard customize screen. */
    val dashboardWidgetStyle: Boolean
}
