package com.georgeci.moneysurfer.appconfig

/**
 * Host-declared facts, replacing the `OfflineBuildFlags` / `SignInFeatureConfig` /
 * `TransactionCreationFeatureConfig` / `SyncFeatureFlag` data classes and their per-host Koin
 * bindings.
 *
 * These are the one group of keys that is **public**: each host has to enumerate them when it
 * declares its Build layer. Features still cannot reach them — the fence is the dependency graph,
 * not visibility.
 *
 * None of them is `remoteOverridable`. Host wiring must stay host-owned: a server able to flip
 * `host.is_offline` would put the online build on the offline start-route branch while its DI
 * graph stays fully online, which is exactly the property the per-host bindings existed to
 * protect.
 *
 * The declared [ConfigKey.default] is the *online* value in every case, so a host that forgets a
 * key degrades to the fuller surface rather than to a silently crippled one — except
 * [syncEnabled], which is off in both hosts today because the feature is not shipped.
 */
object HostConfigKeys {

    /** `true` in the Firebase-free offline host. Gates profile, sync, members, invites, logout. */
    val isOffline: ConfigKey<Boolean> = ConfigKey.bool("host.is_offline", default = false)

    val signInEmailPassword: ConfigKey<Boolean> = ConfigKey.bool("host.sign_in_email_password", default = true)

    val signInAnonymous: ConfigKey<Boolean> = ConfigKey.bool("host.sign_in_anonymous", default = true)

    val signInDemo: ConfigKey<Boolean> = ConfigKey.bool("host.sign_in_demo", default = true)

    /** Multi-account transfers — out of scope for the offline MVP. */
    val transferEnabled: ConfigKey<Boolean> = ConfigKey.bool("host.transfer_enabled", default = true)

    /**
     * Build-owned term of `SyncSettings.isEnabled`. Lives here rather than next to the other sync
     * keys because hosts have to declare it, and it is `false` in *both* hosts today — a
     * `remote && user` pair would have no slot for it and sync would silently switch on.
     */
    val syncEnabled: ConfigKey<Boolean> = ConfigKey.bool("host.sync_enabled", default = false)

    val all: List<ConfigKey<*>> = listOf(
        isOffline,
        signInEmailPassword,
        signInAnonymous,
        signInDemo,
        transferEnabled,
        syncEnabled,
    )
}
