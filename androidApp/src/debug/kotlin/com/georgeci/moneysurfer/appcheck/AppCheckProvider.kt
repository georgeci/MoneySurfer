package com.georgeci.moneysurfer.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-build attestation. Play Integrity needs a Play-signed build on a device that passes
 * integrity checks, which no emulator and no locally-signed debug APK does — so debug builds
 * use the debug provider instead.
 *
 * It prints a UUID to logcat on first run; that secret has to be registered under
 * App Check → Apps → Manage debug tokens before the project accepts it. Anyone holding a
 * registered secret can mint valid tokens, which is why the artifact backing this file is
 * wired as `debugImplementation` and the release variant has its own
 * [installAppCheckProvider] in `src/release`.
 */
fun installAppCheckProvider() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
