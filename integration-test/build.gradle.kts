plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    jvm()

    android {
        namespace = "com.georgeci.moneysurfer.integration"

        // Gradle-Managed Device for the hermetic CI path (`qaIntegrationDeviceHermetic`).
        // Boots a headless Pixel 6 / API 34 AVD on demand, runs the device-IT, and tears
        // it down — no manually-launched emulator required.
        //
        // `aosp-atd` (Automated Test Device) trims out Play Services + UI shell, which we
        // don't need (gitlive Firebase ships its own native client; we drive it against the
        // local emulator). Boots ~3× faster than `aosp` on first use, and the system image
        // is smaller to download. If you ever wire a test that needs Play Services on the
        // device, switch back to `aosp` or `google` source.
        //
        // Generated tasks (off this single device entry):
        //   :integration-test:integrationAvdSetup       — downloads + provisions the system image
        //   :integration-test:integrationAvdAndroidTest — runs the connected suite on the AVD
        //
        // Why API 34 specifically: matches the local AVD the manual path was tested on
        // (`Pixel_3a_API_34_extension_level_7_arm64-v8a`) so the rule + Firestore-emulator
        // quirks are validated under the same Android version.
        //
        // Why `compilations.withType(...)` instead of `withDeviceTest { ... }`:
        // the convention plugin (`kmp.lib`) already calls `withDeviceTestBuilder { ... }
        // .configure { ... }` to register the device test compilation, so a second
        // top-level `withDeviceTest` block fails with "Android device tests have
        // already been enabled". `compilations.withType` configures the existing
        // compilation in-place — it implements `KotlinMultiplatformAndroidDeviceTest`,
        // which is where `managedDevices` lives.
        compilations.withType(
            com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTestCompilation::class.java,
        ) {
            managedDevices {
                localDevices {
                    create("integrationAvd") {
                        device = "Pixel 6"
                        apiLevel = 34
                        systemImageSource = "aosp-atd"
                    }
                }
            }
        }
    }

    sourceSets {
        // ── JVM integration suite ────────────────────────────────────────────
        // Phase 3: Room-only integration. gitlive's "JVM" Firebase artefact
        // imports `android.content.Context` and crashes on plain JVM, so we
        // can't drive Firestore from here. Real Firestore coverage lives in
        // `androidDeviceTest` below.
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)

            // Modules under test
            implementation(projects.domain)
            implementation(projects.sync.default)
            implementation(projects.dataLocal)
            implementation(projects.dataRemote)
            implementation(projects.syncSurfer)

            // Fixtures
            implementation(projects.domainTestFixtures)
            implementation(projects.syncTestFixtures)
            implementation(projects.dataTestFixtures)

            implementation(libs.gitlive.firebase.common)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)

            // Real Room (in-memory) — JVM Room runtime supports in-memory databases.
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
        }

        // ── Android device test suite ────────────────────────────────────────
        // Runs on a real Android device or AVD. Drives the full domain → sync →
        // data → gitlive Firestore stack against the local Firebase Emulator
        // (host's `localhost:8080` reaches the AVD as `10.0.2.2:8080`).
        //
        // Why a separate target rather than expanding `:data`'s androidDeviceTest:
        // the test surface is the *integration* of multiple modules — domain use
        // cases reaching through the outbox into Firestore — not anything that
        // belongs in the data module's per-impl coverage.
        getByName("androidDeviceTest").dependencies {
            // Modules under test
            implementation(projects.domain)
            implementation(projects.sync.default)
            implementation(projects.dataLocal)
            implementation(projects.dataRemote)
            implementation(projects.syncSurfer)

            // Fixtures
            implementation(projects.domainTestFixtures)
            implementation(projects.syncTestFixtures)
            implementation(projects.dataTestFixtures)

            // gitlive Firebase Android — production stack on the device.
            implementation(libs.gitlive.firebase.common)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)

            // Native Firebase Android (gitlive delegates to it under the hood,
            // and we need the BoM to align versions).
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)

            // Room (Android runtime, in-memory).
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            // Test plumbing
            implementation(libs.androidx.runner)
            implementation(libs.androidx.core)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.arrow.core)
        }
    }
}

// `:integration-test:jvmTest` is gated on a running Firebase Emulator Suite.
// Sequential because emulator state is global; parallelism would interleave writes.
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    // Honour Kotest's tag policy — useful when individual specs add `@Tags("emulator")`
    // but harmless when they don't (the default `:integration-test:jvmTest` task always
    // assumes the emulator is up).
    maxParallelForks = 1
}

tasks.register("integrationTest") {
    description = "Alias for jvmTest — runs the JVM integration suite against the Firebase Emulator."
    group = "verification"
    dependsOn("jvmTest")
}
