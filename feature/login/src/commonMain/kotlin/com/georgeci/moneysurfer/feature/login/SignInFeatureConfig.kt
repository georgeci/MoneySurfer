package com.georgeci.moneysurfer.feature.login

/**
 * Per-auth-type visibility switches for the sign-in screen. The online build
 * enables every entry point; the offline build (`composeAppOffline`) only
 * leaves [demo] on, since it has no remote auth backend wired up.
 *
 * Each host registers its own instance in Koin (see `OnlineSignInModule` /
 * `offlineSignInModule`) so the offline configuration cannot silently regress
 * via Koin module load order.
 */
data class SignInFeatureConfig(
    val emailPassword: Boolean = true,
    val anonymous: Boolean = true,
    val demo: Boolean = true,
) {
    /** True when the screen should render only the demo CTA. */
    val demoOnly: Boolean
        get() = demo && !emailPassword && !anonymous
}
