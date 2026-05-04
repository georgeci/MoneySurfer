package com.georgeci.moneysurfer.domain.repositories

/**
 * Toggle for the Firebase Emulator Suite. Bound by `:data` per platform; tests
 * override with a fixture that always returns `useEmulator = true`.
 *
 * - [useEmulator] — when `true`, the SDK is redirected to a local Firestore + Auth
 *   emulator BEFORE the first read/write. Production builds keep this `false`.
 * - [emulatorHost] — Android emulator reaches the host machine via `10.0.2.2`,
 *   iOS simulator and Desktop via `localhost`. Implementations resolve this
 *   per-platform.
 * - [authPort] / [firestorePort] — match `firebase.json` (defaults 9099 / 8080).
 *
 * See md/sync.md emulator section.
 */
interface FirebaseConfig {
    val useEmulator: Boolean
    val emulatorHost: String
    val authPort: Int
    val firestorePort: Int
}
