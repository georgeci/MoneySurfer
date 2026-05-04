package com.georgeci.moneysurfer.data.emulator

import com.georgeci.moneysurfer.data.fixtures.EmulatorClient
import com.georgeci.moneysurfer.data.fixtures.ensureEmulatorReady
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue

/**
 * Validates the Phase 2 emulator-test scaffolding end-to-end:
 * - readiness probe pings both Auth and Firestore HTTP endpoints,
 * - `reset()` REST calls return 2xx without exception.
 *
 * Tagged `emulator` — excluded from default `:data:jvmTest`, included in
 * `:data:emulatorTest`. Requires a running emulator suite (see
 * [md/Firebase_Emulator_Suite.md](../../../../../../../md/Firebase_Emulator_Suite.md)).
 *
 * Why the support-only test rather than a full gitlive-Firestore round-trip:
 * gitlive's JVM port needs `FirebaseApp.initializeApp(options)` plumbing that
 * doesn't exist in `:data` yet. The richer integration test lives in
 * `:integration-test` (Phase 3). Phase 2 validates the harness layer.
 */
@Tags("emulator")
class EmulatorClientSmokeSpec : StringSpec({

    val client = EmulatorClient()

    beforeSpec { ensureEmulatorReady(client) }

    "isReady reports true once both emulators respond" {
        client.isReady().shouldBeTrue()
    }

    "reset succeeds without throwing on a fresh emulator" {
        // Reset is the contract every emulator-backed spec relies on for
        // hermetic state. Failing here means the project id in EmulatorClient
        // doesn't match the one the emulator was booted with — see
        // EmulatorFirebaseConfig.DEFAULT_PROJECT_ID (`demo-moneysurfer`).
        client.reset()
    }
})
