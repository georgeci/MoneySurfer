package com.georgeci.moneysurfer.feature.login

/**
 * Build-flavor switches for the sign-in screen. The online build keeps the
 * default (full email/password + anonymous + demo). The offline build
 * (`composeAppOffline`) overrides this in its Koin wiring to render only the
 * demo entry point.
 */
data class SignInFeatureConfig(
    val demoOnly: Boolean = false,
)
