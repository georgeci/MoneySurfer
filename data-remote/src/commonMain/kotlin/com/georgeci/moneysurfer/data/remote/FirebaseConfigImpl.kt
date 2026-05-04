package com.georgeci.moneysurfer.data.remote

import com.georgeci.moneysurfer.domain.repositories.FirebaseConfig
import org.koin.core.annotation.Single

/**
 * Default `FirebaseConfig` binding. The toggle [useEmulator] is sourced from
 * [defaultUseEmulator] (per-platform `expect/actual`) — Android pulls
 * `BuildConfig.USE_EMULATOR`, JVM/iOS read an env var. Tests bind their own
 * `FirebaseConfig` instead of using this one.
 *
 * Ports are static (matching `firebase.json`); the host is platform-specific
 * (`10.0.2.2` for Android emulator, `localhost` elsewhere).
 */
@Single(binds = [FirebaseConfig::class])
class FirebaseConfigImpl : FirebaseConfig {
    override val useEmulator: Boolean = defaultUseEmulator()
    override val emulatorHost: String = defaultEmulatorHost()
    override val authPort: Int = DEFAULT_AUTH_PORT
    override val firestorePort: Int = DEFAULT_FIRESTORE_PORT

    private companion object {
        const val DEFAULT_AUTH_PORT: Int = 9099
        const val DEFAULT_FIRESTORE_PORT: Int = 8080
    }
}

/**
 * Whether this build should target the emulator by default. Android maps to
 * `BuildConfig.USE_EMULATOR`, JVM to env var `MS_USE_EMULATOR`, iOS to a plist
 * flag (or hardcoded `false` for now).
 */
internal expect fun defaultUseEmulator(): Boolean

/**
 * The hostname over which the host machine is reachable from this platform's
 * runtime. Android emulator: `10.0.2.2`. iOS simulator + Desktop: `localhost`.
 */
internal expect fun defaultEmulatorHost(): String
