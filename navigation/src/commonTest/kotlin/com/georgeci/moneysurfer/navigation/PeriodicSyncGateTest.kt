package com.georgeci.moneysurfer.navigation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The kill-switch half of `AppLaunchViewModel`: which uid the background ticker may run for.
 *
 * Extracted from the view model for the same reason [resolveStartRoute] is — it has branches worth
 * pinning down, and pinning them here needs neither a view model scope nor a one-minute clock.
 * The mid-session case runs on a test dispatcher because `flatMapLatest` over a hot source is racy
 * otherwise: the outer flow can cancel an inner subscription before it ever emits.
 */
class PeriodicSyncGateTest : StringSpec({

    "nothing is emitted while sync is disabled" {
        // Zero Firestore reads on the background path is the point: the uid flow must not even be
        // subscribed, let alone produce a ticker.
        periodicSyncUids(
            isSyncEnabled = flowOf(false),
            firebaseUid = flowOf("uid-1"),
        ).toList().shouldBeEmpty()
    }

    "an enabled session with a Firebase uid runs the ticker for that uid" {
        periodicSyncUids(
            isSyncEnabled = flowOf(true),
            firebaseUid = flowOf("uid-1"),
        ).toList() shouldBe listOf("uid-1")
    }

    "a signed-out or demo session never starts the ticker" {
        periodicSyncUids(
            isSyncEnabled = flowOf(true),
            firebaseUid = flowOf(null, ""),
        ).toList().shouldBeEmpty()
    }

    "a retracted kill switch stops the gate, and restoring it starts a fresh ticker" {
        runTest {
            val enabled = MutableStateFlow(true)
            val uid = MutableStateFlow<String?>("uid-1")
            val seen = mutableListOf<String>()

            val collector = backgroundScope.launch {
                periodicSyncUids(isSyncEnabled = enabled, firebaseUid = uid).collect { seen += it }
            }
            runCurrent()
            seen shouldBe listOf("uid-1")

            // Server retracts the switch. The gate goes quiet, which is what makes `collectLatest`
            // in the view model cancel the running loop; a uid change while off starts nothing.
            enabled.value = false
            uid.value = "uid-2"
            runCurrent()
            seen shouldBe listOf("uid-1")

            // Restoring it resubscribes and picks up the *current* uid, not the stale one.
            enabled.value = true
            runCurrent()
            seen shouldBe listOf("uid-1", "uid-2")

            collector.cancel()
        }
    }
})
