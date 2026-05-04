package com.georgeci.moneysurfer.data.fixtures

/**
 * Spec-level guard for emulator-backed tests.
 *
 * Usage in a Kotest `StringSpec`:
 *
 * ```
 * class FooEmulatorSpec : StringSpec({
 *     val client = EmulatorClient()
 *     beforeSpec { ensureEmulatorReady(client) }
 *     beforeEach { client.reset() }
 *
 *     "..." { ... }
 * })
 * ```
 *
 * Throws a clear error message when the emulator isn't reachable so the failure
 * doesn't surface as a downstream `Connection refused` in the SDK layer.
 */
fun ensureEmulatorReady(
    client: EmulatorClient = EmulatorClient(),
    timeoutMillis: Long = 30_000L,
) {
    val ready = client.waitUntilReady(timeoutMillis)
    check(ready) {
        """
        |Firebase Emulator Suite isn't reachable on
        |  ${EmulatorFirebaseConfig.DEFAULT_HOST}:${EmulatorFirebaseConfig.DEFAULT_AUTH_PORT} (auth)
        |  ${EmulatorFirebaseConfig.DEFAULT_HOST}:${EmulatorFirebaseConfig.DEFAULT_FIRESTORE_PORT} (firestore)
        |after ${timeoutMillis}ms.
        |
        |Start it with: scripts/firebase/start.sh
        |Or wrap the test run with: firebase emulators:exec '<command>'
        """.trimMargin()
    }
}
