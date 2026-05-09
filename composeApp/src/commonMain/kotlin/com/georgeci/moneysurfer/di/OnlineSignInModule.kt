package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.domain.OfflineBuildFlags
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
}
