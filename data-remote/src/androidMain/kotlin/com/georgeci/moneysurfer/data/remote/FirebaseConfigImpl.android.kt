package com.georgeci.moneysurfer.data.remote

/**
 * On Android the host machine running the emulator suite is reachable via
 * `10.0.2.2` from within the Android emulator (the special-purpose alias the
 * AVD assigns to the host loopback). Real-device runs against an emulator
 * suite would need a LAN IP — out of scope here.
 */
internal actual fun defaultEmulatorHost(): String = "10.0.2.2"

/**
 * Two-channel toggle:
 *
 * 1. **System property** `MS_USE_EMULATOR=true` — set by `MoneySurferApplication`
 *    when `BuildConfig.USE_EMULATOR == true` (compile-time flag from
 *    `-PuseEmulator=true` Gradle property). Standard path for emulator
 *    debug builds installed on an AVD.
 * 2. **Env var** `MS_USE_EMULATOR=true` — fallback for direct `gradlew run`-style
 *    invocations or instrumentation tests where there's no Application class
 *    to set the system property.
 *
 * Production APKs never see either set, so `defaultUseEmulator() == false`.
 */
internal actual fun defaultUseEmulator(): Boolean {
    val sysProp = System.getProperty("MS_USE_EMULATOR")?.equals("true", ignoreCase = true) == true
    val envVar = System.getenv("MS_USE_EMULATOR")?.equals("true", ignoreCase = true) == true
    return sysProp || envVar
}
