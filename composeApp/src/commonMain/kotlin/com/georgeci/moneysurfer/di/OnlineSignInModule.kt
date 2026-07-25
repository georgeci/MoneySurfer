package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.feature.login.SignInFeatureConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Online build registers the full sign-in flow (email/password, anonymous,
 * demo) and the [OfflineBuildFlags] flag with `isOffline = false`. Mirrors
 * `composeAppOffline`'s overrides; keeping these bindings host-owned (instead
 * of defaulted in feature modules) means the offline build can't silently
 * regress to the full UI through module load order changes.
 */
@Module
class OnlineSignInModule {

    @Single
    fun signInFeatureConfig(): SignInFeatureConfig = SignInFeatureConfig(
        emailPassword = true,
        anonymous = true,
        demo = true,
    )

    @Single
    fun offlineBuildFlags(): OfflineBuildFlags = OfflineBuildFlags(isOffline = false)

    /**
     * Sync is live in the online build: Settings → Sync, the periodic in-process ticker, and the
     * use-case driven triggers (PostAuthBootstrap, CreateWorkspace, AcceptInvite,
     * RefreshIncomingInvites). See [SyncFeatureFlag] for the full gating surface.
     *
     * Enabled in issue #342, once its preconditions landed: the remote user document is no longer
     * filled with refs to workspaces that were never created, a stale ref no longer blocks
     * sign-in, the pull drains instead of stopping at 100 documents per collection, and a
     * workspace pushed from the outbox registers its own ref. Turning this back off is a release
     * decision — record it in AGENTS.md → "Feature flags shipped switched off".
     */
    @Single
    fun syncFeatureFlag(): SyncFeatureFlag = SyncFeatureFlag(enabled = true)
}
