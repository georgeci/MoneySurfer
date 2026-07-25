package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.appconfig.BuildConfigSource
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.appconfig.RemoteGlobalConfigSource
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * The online host's Build layer, replacing the four flag data classes (`OfflineBuildFlags`,
 * `SignInFeatureConfig`, `TransactionCreationFeatureConfig`, `SyncFeatureFlag`) and their bindings.
 *
 * Declared here rather than defaulted inside the engine so host wiring stays host-owned: the
 * offline build cannot regress to the online surface through Koin module load order, which is the
 * property the previous per-host bindings existed to protect. None of these keys is
 * `remoteOverridable`, so the server cannot flip them either.
 */
@Module
class OnlineHostConfigModule {

    @Single
    fun buildConfigSource(): BuildConfigSource = BuildConfigSource {
        put(HostConfigKeys.isOffline, false)
        put(HostConfigKeys.signInEmailPassword, true)
        put(HostConfigKeys.signInAnonymous, true)
        put(HostConfigKeys.signInDemo, true)
        put(HostConfigKeys.transferEnabled, true)
        // Sync is live in the online build since issue #342: Settings → Sync, the periodic
        // in-process ticker, and the use-case-driven triggers (PostAuthBootstrap, CreateWorkspace,
        // AcceptInvite, RefreshIncomingInvites). This is the build-owned term of
        // `SyncSettings.isEnabled`, so the server kill switch and the user toggle can only narrow
        // it. Turning it back off is a release decision — record it in AGENTS.md → "Feature flags
        // shipped switched off".
        put(HostConfigKeys.syncEnabled, true)
    }

    /**
     * No remote layer yet: `app-config/remote` and the `appConfig/flags` document land with the
     * follow-up issue. Until then every `remoteOverridable` key falls through to Build.
     */
    @Single
    fun remoteGlobalConfigSource(): RemoteGlobalConfigSource = RemoteGlobalConfigSource.Empty
}
