package com.georgeci.moneysurfer.data.remote

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

private const val API_KEY_LENGTH = 39

/**
 * Picks which Firebase project the desktop host talks to. Getting this wrong is silent
 * rather than loud: pointing at a project the emulator was not booted with just returns
 * empty reads, so the branching is pinned here rather than left to a manual run.
 */
class DesktopFirebaseOptionsJvmTest : StringSpec({

    fun noEnv(): (String) -> String? = { null }

    "emulator runs default to the shared demo project" {
        val options = desktopFirebaseOptions(useEmulator = true, env = noEnv())

        options.projectId shouldBe "demo-moneysurfer"
    }

    // Regression: the desktop host used to hardcode the project id, so a developer who
    // booted the emulator on another id via FIREBASE_PROJECT_ID silently read an empty
    // namespace — the same mismatch the scripts/firebase defaults were realigned to fix.
    "emulator runs follow FIREBASE_PROJECT_ID so they track the booted emulator" {
        val options = desktopFirebaseOptions(
            useEmulator = true,
            env = { name -> "demo-other".takeIf { name == "FIREBASE_PROJECT_ID" } },
        )

        options.projectId shouldBe "demo-other"
    }

    "a blank FIREBASE_PROJECT_ID falls back rather than yielding an empty project" {
        val options = desktopFirebaseOptions(
            useEmulator = true,
            env = { name -> "   ".takeIf { name == "FIREBASE_PROJECT_ID" } },
        )

        options.projectId shouldBe "demo-moneysurfer"
    }

    // FirebaseInstallations validates the key's shape even against the emulator, and a
    // malformed one killed every iOS E2E run before the sign-in screen appeared (#219).
    "the dummy emulator API key keeps the format FirebaseInstallations demands" {
        val apiKey = desktopFirebaseOptions(useEmulator = true, env = noEnv()).apiKey

        apiKey.length shouldBe API_KEY_LENGTH
        apiKey shouldStartWith "AI"
    }

    "a real project is assembled from the three MS_FIREBASE variables" {
        val options = desktopFirebaseOptions(
            useEmulator = false,
            env = { name ->
                when (name) {
                    "MS_FIREBASE_APP_ID" -> "1:1234:android:abcd"
                    "MS_FIREBASE_API_KEY" -> "AIza" + "x".repeat(API_KEY_LENGTH - "AIza".length)
                    "MS_FIREBASE_PROJECT_ID" -> "moneysurfer-dev"
                    else -> null
                }
            },
        )

        options.applicationId shouldBe "1:1234:android:abcd"
        options.projectId shouldBe "moneysurfer-dev"
    }

    // Without this the missing variable surfaced as a Koin resolution stack ending in
    // AppViewModel, which says nothing about what the developer has to set.
    "an unconfigured real-project run names the missing variable" {
        val failure = shouldThrow<IllegalStateException> {
            desktopFirebaseOptions(useEmulator = false, env = noEnv())
        }

        failure.message.orEmpty() shouldContain "MS_FIREBASE_APP_ID"
        failure.message.orEmpty() shouldContain "MS_USE_EMULATOR=true"
    }
})
