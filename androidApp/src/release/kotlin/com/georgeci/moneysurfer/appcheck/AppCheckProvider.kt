package com.georgeci.moneysurfer.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release-build attestation. Play Integrity asks Google to vouch that this really is the
 * Play-signed MoneySurfer running on a genuine Android device, which is the whole point of
 * App Check — Firestore rules can tell who the user is, but not what client is asking.
 *
 * The debug counterpart in `src/debug` deliberately does not exist here: its artifact is
 * wired as `debugImplementation`, so a release binary cannot fall back to minting tokens
 * from a shared secret.
 */
fun installAppCheckProvider() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
