package com.georgeci.moneysurfer.data.fixtures

import com.georgeci.moneysurfer.domain.repositories.FirebaseConfig

/**
 * `FirebaseConfig` binding for tests that talk to a locally-running Firebase
 * Emulator Suite. Match these defaults to `firebase.json` — the Koin override
 * in [com.georgeci.moneysurfer.data.di.DataModule] reads `emulatorHost` /
 * `*Port` and calls `useEmulator(...)` before the SDK's first read/write.
 *
 * Override [host] from a CI runner if the emulator is reachable on a different
 * IP (e.g. macOS-on-Linux Docker bridges).
 */
data class EmulatorFirebaseConfig(
    val host: String = DEFAULT_HOST,
    val auth: Int = DEFAULT_AUTH_PORT,
    val firestore: Int = DEFAULT_FIRESTORE_PORT,
) : FirebaseConfig {
    override val useEmulator: Boolean = true
    override val emulatorHost: String = host
    override val authPort: Int = auth
    override val firestorePort: Int = firestore

    companion object {
        const val DEFAULT_HOST: String = "localhost"
        const val DEFAULT_AUTH_PORT: Int = 9099
        const val DEFAULT_FIRESTORE_PORT: Int = 8080

        /**
         * Shared emulator project id across all test surfaces:
         * `firestore-tests/` (mocha rules suite), `:integration-test`
         * (`EmulatorEnv.EMULATOR_PROJECT_ID`), and Gradle-orchestrated
         * Maestro/IT runs via `firebase emulators:exec --project ...`.
         * `demo-` prefix opts the Firebase tooling into demo-project mode
         * (no real project / no auth credentials).
         */
        const val DEFAULT_PROJECT_ID: String = "demo-moneysurfer"
    }
}
