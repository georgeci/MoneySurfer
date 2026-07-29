package com.georgeci.moneysurfer.data.remote

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The default binding decides whether a build talks to the real Firebase project or to a local
 * emulator, so the two things worth pinning are that a developer machine with nothing set defaults
 * to the real one, and that the ports match `firebase.json` — a mismatch shows up as a hang rather
 * than an error.
 */
class FirebaseConfigImplJvmTest : StringSpec({

    "an unset environment leaves the desktop build pointed at the real project" {
        // MS_USE_EMULATOR is not set in the test JVM; the emulator path is opt-in.
        FirebaseConfigImpl().useEmulator shouldBe (
            System.getenv("MS_USE_EMULATOR")?.equals("true", ignoreCase = true) == true
            )
    }

    "the desktop host reaches the emulator over loopback" {
        FirebaseConfigImpl().emulatorHost shouldBe "localhost"
    }

    "the ports match the emulator suite's defaults" {
        val config = FirebaseConfigImpl()

        config.authPort shouldBe 9099
        config.firestorePort shouldBe 8080
    }
})
