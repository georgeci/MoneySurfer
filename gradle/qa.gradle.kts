// QA pipeline: tests + Kover + Allure for common, Android host, Android device, Maestro.
// Loaded from root build.gradle.kts via `apply(from = "gradle/qa.gradle.kts")`.

import com.georgeci.moneysurfer.buildlogic.AllureTools
import com.georgeci.moneysurfer.buildlogic.MaestroTools
import org.gradle.api.tasks.Exec
import java.io.FileOutputStream

// The helper layer this script used to declare inline (adb/maestro/allure
// discovery, JUnit summarising, log tails) now lives in the `build-logic`
// included build as `MaestroTools` / `AllureTools`, put on this script's
// classpath by the `ms.qa-tools` marker declared in `settings.gradle.kts`.
//
// The move is what makes these tasks configuration-cache clean: a `doFirst` /
// `doLast` that calls a script-level function captures the compiled script
// instance — and, via `rootProject` / `project.delete`, the `Project` — neither
// of which can be serialized. Gradle reported those captures on every Maestro
// run ("Configuration cache entry discarded with N problems") regardless of the
// `notCompatibleWithConfigurationCache(...)` opt-outs, which is why the opt-outs
// are gone from the tasks below.
//
// Rules of thumb when touching the task bodies:
//   - a task action may only reference `MaestroTools` / `AllureTools`, `File`s
//     and `Provider`s captured in the surrounding configuration block, and the
//     task itself;
//   - never `project`, `rootProject`, `rootDir` or a script-level `val`/`fun`
//     from inside `doFirst` / `doLast` — copy the value into a local first.
val repoRoot: File = rootProject.projectDir

val androidMaestroAppId = MaestroTools.ANDROID_APP_ID
val iosMaestroAppId = MaestroTools.IOS_APP_ID
val offlineMaestroAppId = MaestroTools.OFFLINE_APP_ID

val debugApkPath = rootProject.file("androidApp/build/outputs/apk/debug/androidApp-debug.apk")
val maestroReportsDir = rootProject.file("build/test-results/maestro")
val maestroAllFlowsJunit = maestroReportsDir.resolve("maestro-report.xml")
val maestroLogsDir = rootProject.file("build/logs/maestro")
val maestroDebugDir = rootProject.file("build/maestro-debug")
val maestroArtifactsDir = rootProject.file("build/maestro-artifacts")
val maestroAllureResultsDir = rootProject.file("build/allure-results/maestro")
val maestroIosReportsDir = rootProject.file("build/test-results/maestro-ios")
val maestroIosAllFlowsJunit = maestroIosReportsDir.resolve("maestro-ios-report.xml")
val maestroIosLogsDir = rootProject.file("build/logs/maestro-ios")
val maestroIosDebugDir = rootProject.file("build/maestro-ios-debug")
val maestroIosArtifactsDir = rootProject.file("build/maestro-ios-artifacts")
val maestroIosAllureResultsDir = rootProject.file("build/allure-results/maestro-ios")
val maestroEmulatorEnv = MaestroTools.loadMaestroTestUser(repoRoot) + mapOf("APP_ID" to androidMaestroAppId)
val maestroIosEmulatorEnv = MaestroTools.loadMaestroTestUser(repoRoot) + mapOf("APP_ID" to iosMaestroAppId)
val iosMaestroDeviceId = providers.gradleProperty("iosSimulatorUdid").orNull
    ?: System.getenv("IOS_SIMULATOR_UDID")
// Tags kept out of the default (online) Maestro suites:
//  - `setup`   : reusable login fragment, not a standalone flow.
//  - `offline` : offline-build golden path — different appId, no Firebase
//                emulator. Run via `qaMaestroOfflineAndroid` / `qaMaestroOfflineIos`.
//  - `sync`    : needs the Sync hub. `host.sync_enabled` is `true` in the online
//                build since #342, so these flows can be un-tagged once they have
//                been run green against the emulator — see 14_force_sync_now.yaml.
//  - `smoke`   : the iOS launch smoke (`scripts/maestro/ios/app-open.yaml`),
//                driven directly by the iOS tasks. It sits in a subdirectory
//                the suite targets don't scan anyway — this is belt and braces.
val maestroSetupTags = listOf("setup", "offline", "sync", "smoke")

// Sole iOS flow while the iOS suites are cut back to a launch smoke (#297).
// `qaMaestroIos` and `qaMaestroOfflineIos` both point here; the flow reads its
// bundle id from `APP_ID`, so the same file covers the online and offline apps.
val iosSmokeFlow = "scripts/maestro/ios/app-open.yaml"

val iosMaestroDerivedDataDir = rootProject.file("build/ios-maestro")
// The online Xcode config (`iosApp/Configuration/Config.xcconfig`) overrides
// `PRODUCT_NAME` to `MoneySurfer Dev` for the Debug configuration (the `.dev`
// flavor mirrored from Android), so the `-configuration Debug` build emits a
// bundle under that (space-containing) name — not `MoneySurfer.app`. Same shape
// as `iosOfflineMaestroAppPath` below.
val iosMaestroAppPath = iosMaestroDerivedDataDir.resolve("Build/Products/Debug-iphonesimulator/MoneySurfer Dev.app")

val firestoreTestsDir = rootProject.file("firestore-tests")
val firestoreReportsDir = rootProject.file("build/test-results/firestore")
val firestoreJunit = firestoreReportsDir.resolve("firestore-report.xml")

val allureRootDir = rootProject.file("build/reports/allure")
val allureCommonDir = allureRootDir.resolve("common")
val allureAndroidHostDir = allureRootDir.resolve("android-host")
val allureAndroidDeviceDir = allureRootDir.resolve("android-device")
val allureMaestroDir = allureRootDir.resolve("maestro")
val allureMaestroIosDir = allureRootDir.resolve("maestro-ios")
val allureFirestoreDir = allureRootDir.resolve("firestore")
val allureAllDir = allureRootDir.resolve("all")

fun testOwners(vararg sourceSets: String): List<Project> =
    subprojects.filter { subproject ->
        sourceSets.any { sourceSet ->
            subproject.projectDir.resolve("src/$sourceSet").isDirectory
        }
    }

val commonTestOwners = testOwners("commonTest", "jvmTest")
val androidHostTestOwners = testOwners("commonTest", "androidHostTest")

val androidDeviceScopeModules = listOf(
    "androidApp", "shared", "domain",
    "data-local", "data-remote",
    "sync/api", "sync/default", "sync-surfer",
    "uikit", "integration-test",
)

fun moduleAndroidDeviceResults(module: String): File =
    rootProject.file("$module/build/outputs/androidTest-results")

val commonScopeAllureSources: List<File> =
    commonTestOwners.map { it.layout.buildDirectory.dir("test-results/jvmTest").get().asFile }

val androidHostScopeAllureSources: List<File> =
    androidHostTestOwners.map {
        it.layout.buildDirectory.dir("test-results/testAndroidHostTest").get().asFile
    }

val androidDeviceScopeAllureSources: List<File> =
    androidDeviceScopeModules.map(::moduleAndroidDeviceResults)

val maestroAllureSources: List<File> = listOf(maestroAllureResultsDir)
val maestroIosAllureSources: List<File> = listOf(maestroIosAllureResultsDir)

val firestoreAllureSources: List<File> = listOf(firestoreReportsDir)

val allScopeAllureSources: List<File> =
    (
        commonScopeAllureSources +
            androidHostScopeAllureSources +
            androidDeviceScopeAllureSources
        ).distinct()

fun registerAllureGenerate(name: String, scopeLabel: String, sources: List<File>, output: File) {
    tasks.register<Exec>(name) {
        group = "verification"
        description = "Generate Allure report for $scopeLabel scope into ${output.relativeTo(repoRoot)}."
        // Best-effort: a hard fail here would silently swallow the underlying
        // test-task failure (Gradle reports the *last* task's exit code). On
        // the green path `allure generate` returns 0 anyway; on the red path
        // we'd rather still upload whatever HTML did make it to disk.
        isIgnoreExitValue = true
        doFirst {
            // Built at execution time on purpose: the input directories are
            // produced by the very test tasks this report aggregates, so a
            // command line resolved during configuration would be stale as soon
            // as the configuration cache replays it.
            commandLine(AllureTools.generateCommand(sources, output))
        }
        doLast {
            val exit = executionResult.get().exitValue
            if (exit != 0) {
                logger.warn(
                    "[allure] $name exited with $exit (scope=$scopeLabel). " +
                        "HTML output (may be partial): ${output.absolutePath}",
                )
            }
        }
    }
}

// All Maestro tasks build with -PuseEmulator=true and talk exclusively to the
// local Firebase Emulator Suite. Running against the production project is not
// supported. Credentials are read from scripts/e2e-test-user.properties and
// seeded into the emulator via scripts/firebase/seed.sh.
//
// `:androidApp:assembleDebug` honours the `-PuseEmulator=true` Gradle property
// (see androidApp/build.gradle.kts) — the resulting APK has
// `BuildConfig.USE_EMULATOR = true` which points FirebaseConfigImpl.android at
// `10.0.2.2:8080 / 10.0.2.2:9099` (host from inside the Android AVD).
//
// The build is launched via a `./gradlew` subprocess so that Gradle's
// task-output cache distinguishes `USE_EMULATOR=true` from a cached plain APK.

tasks.register<Exec>("maestroAssembleDebug") {
    group = "verification"
    description = "Build debug APK with BuildConfig.USE_EMULATOR=true for Maestro E2E tests."
    workingDir = rootDir
    commandLine("./gradlew", ":androidApp:assembleDebug", "-PuseEmulator=true")
}

tasks.register<Exec>("maestroInstallDebug") {
    group = "verification"
    description = "Build USE_EMULATOR=true debug APK and adb-install on connected device/AVD."
    dependsOn("maestroAssembleDebug")
    // adb lives outside the daemon's PATH and the APK is produced by the task
    // we depend on, so both are resolved in doFirst — from captured `File`s and
    // `MaestroTools`, never from the script or `project`.
    val root = repoRoot
    val apk = debugApkPath
    doFirst {
        require(apk.exists()) {
            "Debug APK not found at ${apk.absolutePath}. " +
                ":androidApp:assembleDebug -PuseEmulator=true did not produce its expected output — " +
                "check the assembleDebug log above (DEV signing, processDebugGoogleServices, USE_EMULATOR=true)."
        }
        commandLine(MaestroTools.resolveAdbExecutable(root), "install", "-r", apk.absolutePath)
    }
}

tasks.register<Exec>("maestroRunAll") {
    group = "verification"
    description = "Install APK + run all Maestro flows (Firebase Emulator must be running)."
    dependsOn("maestroInstallDebug")
    val root = repoRoot
    val excludedTags = maestroSetupTags
    doFirst {
        commandLine(MaestroTools.buildMaestroCommand(root, "scripts/maestro/", excludeTags = excludedTags))
    }
}

tasks.register("maestroRunAllAndroid") {
    group = "verification"
    description = "Android alias for maestroRunAll."
    dependsOn("maestroRunAll")
}

tasks.register<Exec>("maestroRunOne") {
    group = "verification"
    description = "Install APK + run one Maestro flow (Firebase Emulator must be running). Pass -PmaestroFlow=05_sign_out.yaml."
    dependsOn("maestroInstallDebug")
    val root = repoRoot
    // Captured as a Provider so `-PmaestroFlow=` stays an input of the
    // configuration-cache entry instead of being read off `project` at
    // execution time.
    val flowProperty = providers.gradleProperty("maestroFlow")
    doFirst {
        val flow = MaestroTools.resolveMaestroFlow(flowProperty.orNull)
        commandLine(MaestroTools.buildMaestroCommand(root, flow))
    }
}

tasks.register("maestroRunOneAndroid") {
    group = "verification"
    description = "Android alias for maestroRunOne. Pass -PmaestroFlow=05_sign_out.yaml."
    dependsOn("maestroRunOne")
}

tasks.register<Exec>("maestroRunAllJunit") {
    group = "verification"
    description = "Install APK, run all flows (Firebase Emulator must be running), write JUnit XML to build/test-results/maestro/maestro-report.xml."
    dependsOn("maestroInstallDebug")
    finalizedBy("allureGenerateMaestro")
    isIgnoreExitValue = true
    val root = repoRoot
    val reportsDir = maestroReportsDir
    val logsDir = maestroLogsDir
    val debugDir = maestroDebugDir
    val artifactsDir = maestroArtifactsDir
    val junit = maestroAllFlowsJunit
    val allureDir = allureMaestroDir
    val excludedTags = maestroSetupTags
    val stdoutLog = maestroLogsDir.resolve("maestroRunAllJunit.out.log")
    val stderrLog = maestroLogsDir.resolve("maestroRunAllJunit.err.log")
    doFirst {
        reportsDir.mkdirs()
        logsDir.mkdirs()
        debugDir.mkdirs()
        artifactsDir.mkdirs()
        standardOutput = FileOutputStream(stdoutLog)
        errorOutput = FileOutputStream(stderrLog)
        logger.lifecycle("[maestro] junit: ${junit.relativeTo(root)}")
        logger.lifecycle("[maestro] logs : ${stdoutLog.relativeTo(root)}, ${stderrLog.relativeTo(root)}")
        commandLine(MaestroTools.buildMaestroCommand(root, "scripts/maestro/", junit, excludeTags = excludedTags))
    }
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            val summary = MaestroTools.summarizeMaestroJunit(junit)
            val tail = MaestroTools.joinLogTails(root, stderrLog, stdoutLog)
            throw GradleException(
                buildString {
                    appendLine("maestroRunAllJunit failed (exit=$exit). $summary")
                    appendLine("Allure HTML (always generated): ${allureDir.absolutePath}")
                    appendLine("Stdout log: ${stdoutLog.absolutePath}")
                    appendLine("Stderr log: ${stderrLog.absolutePath}")
                    appendLine(tail)
                },
            )
        }
    }
}

tasks.register("maestroRunAllAndroidJunit") {
    group = "verification"
    description = "Android alias for maestroRunAllJunit."
    dependsOn("maestroRunAllJunit")
}

tasks.register<Exec>("maestroRunOneJunit") {
    group = "verification"
    description = "Install APK, run one flow (Firebase Emulator must be running), write JUnit XML. Pass -PmaestroFlow=05_sign_out.yaml."
    dependsOn("maestroInstallDebug")
    finalizedBy("allureGenerateMaestro")
    val root = repoRoot
    val reportsDir = maestroReportsDir
    val debugDir = maestroDebugDir
    val artifactsDir = maestroArtifactsDir
    val flowProperty = providers.gradleProperty("maestroFlow")
    doFirst {
        reportsDir.mkdirs()
        debugDir.mkdirs()
        artifactsDir.mkdirs()
        val flow = MaestroTools.resolveMaestroFlow(flowProperty.orNull)
        val flowName = flow.substringAfterLast('/').substringBeforeLast('.')
        val reportFile = reportsDir.resolve("maestro-$flowName.xml")
        commandLine(MaestroTools.buildMaestroCommand(root, flow, reportFile))
    }
}

tasks.register("maestroRunOneAndroidJunit") {
    group = "verification"
    description = "Android alias for maestroRunOneJunit. Pass -PmaestroFlow=05_sign_out.yaml."
    dependsOn("maestroRunOneJunit")
}

tasks.register("maestroRun") {
    group = "verification"
    description = "Alias for maestroRunAllAndroid."
    dependsOn("maestroRunAllAndroid")
}

tasks.register<Exec>("maestroBuildIosSimulator") {
    group = "verification"
    description = "Build iOS Debug simulator app with MS_USE_EMULATOR=YES for Maestro E2E tests."
    workingDir = rootDir
    val simulatorName = providers.gradleProperty("iosSimulatorName").orNull
        ?: System.getenv("IOS_SIMULATOR_NAME")
        ?: "iPhone 17"
    commandLine(
        "xcodebuild",
        "-project", "iosApp/iosApp.xcodeproj",
        "-scheme", "iosApp",
        "-configuration", "Debug",
        "-sdk", "iphonesimulator",
        "-destination", "platform=iOS Simulator,name=$simulatorName",
        "-derivedDataPath", iosMaestroDerivedDataDir.absolutePath,
        "MS_USE_EMULATOR=YES",
        "SKIP_CRASHLYTICS_UPLOAD=YES",
        "build",
    )
}

tasks.register<Exec>("maestroInstallIosSimulator") {
    group = "verification"
    description = "Build and install the iOS simulator app on the booted Simulator."
    dependsOn("maestroBuildIosSimulator")
    val appPath = iosMaestroAppPath
    doFirst {
        require(appPath.exists()) {
            "iOS app not found at ${appPath.absolutePath}. Run maestroBuildIosSimulator first."
        }
        commandLine("xcrun", "simctl", "install", "booted", appPath.absolutePath)
    }
}

tasks.register<Exec>("maestroRunAllIos") {
    group = "verification"
    description = "Install iOS simulator app + run all Maestro flows (Firebase Emulator must be running)."
    dependsOn("maestroInstallIosSimulator")
    val root = repoRoot
    val excludedTags = maestroSetupTags
    val appId = iosMaestroAppId
    val deviceId = iosMaestroDeviceId
    doFirst {
        commandLine(
            MaestroTools.buildMaestroCommand(
                rootDir = root,
                target = "scripts/maestro/",
                excludeTags = excludedTags,
                appId = appId,
                platform = "ios",
                deviceId = deviceId,
            ),
        )
    }
}

tasks.register<Exec>("maestroRunAllIosJunit") {
    group = "verification"
    description = "Install iOS simulator app, run all flows, write JUnit XML to build/test-results/maestro-ios/maestro-ios-report.xml."
    dependsOn("maestroInstallIosSimulator")
    finalizedBy("allureGenerateMaestroIos")
    isIgnoreExitValue = true
    val root = repoRoot
    val reportsDir = maestroIosReportsDir
    val logsDir = maestroIosLogsDir
    val debugDir = maestroIosDebugDir
    val artifactsDir = maestroIosArtifactsDir
    val junit = maestroIosAllFlowsJunit
    val allureDir = allureMaestroIosDir
    val excludedTags = maestroSetupTags
    val appId = iosMaestroAppId
    val deviceId = iosMaestroDeviceId
    val stdoutLog = maestroIosLogsDir.resolve("maestroRunAllIosJunit.out.log")
    val stderrLog = maestroIosLogsDir.resolve("maestroRunAllIosJunit.err.log")
    doFirst {
        reportsDir.mkdirs()
        logsDir.mkdirs()
        debugDir.mkdirs()
        artifactsDir.mkdirs()
        standardOutput = FileOutputStream(stdoutLog)
        errorOutput = FileOutputStream(stderrLog)
        logger.lifecycle("[maestro-ios] junit: ${junit.relativeTo(root)}")
        logger.lifecycle("[maestro-ios] logs : ${stdoutLog.relativeTo(root)}, ${stderrLog.relativeTo(root)}")
        commandLine(
            MaestroTools.buildMaestroCommand(
                rootDir = root,
                target = "scripts/maestro/",
                junitOutput = junit,
                excludeTags = excludedTags,
                appId = appId,
                platform = "ios",
                deviceId = deviceId,
            ),
        )
    }
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            val summary = MaestroTools.summarizeMaestroJunit(junit)
            val tail = MaestroTools.joinLogTails(root, stderrLog, stdoutLog)
            throw GradleException(
                buildString {
                    appendLine("maestroRunAllIosJunit failed (exit=$exit). $summary")
                    appendLine("Allure HTML (always generated): ${allureDir.absolutePath}")
                    appendLine("Stdout log: ${stdoutLog.absolutePath}")
                    appendLine("Stderr log: ${stderrLog.absolutePath}")
                    appendLine(tail)
                },
            )
        }
    }
}

// -- Hermetic integration-test pipeline --------------------------------------
//
// The :integration-test androidDeviceTest suite needs TWO emulators alive:
//   1. Firebase Emulator Suite on the host (firestore + auth)
//   2. An Android AVD with adb visible to Gradle
//
// Two flavours:
//   - `qaIntegrationDeviceEmulator` — assumes you've already booted an AVD via
//     Studio / `emulator -avd <name> &`. Wraps a `firebase emulators:exec`
//     around `:integration-test:connectedAndroidDeviceTest` so the Firestore
//     side is hermetic per invocation.
//   - `qaIntegrationDeviceHermetic` — fully hermetic. Uses the Gradle-Managed
//     Device declared in `integration-test/build.gradle.kts` (`integrationAvd`)
//     so AGP boots / tears down the AVD itself, all inside the Firebase
//     emulator wrapper. Slow on first run (downloads system image once) but
//     CI-friendly afterwards.
//
// Both use `--project demo-moneysurfer` to match `EmulatorEnv.EMULATOR_PROJECT_ID`
// in the integration harness — DO NOT change this without updating the
// instrumented test side, otherwise gitlive Firestore connects to a different
// project namespace and writes silently land in the wrong sandbox.

fun firebaseEmulatorWrap(rootDir: File, gradleSubcommand: List<String>): List<String> {
    // Gradle subcommand is collapsed into a single shell-string because
    // `firebase emulators:exec` takes ONE positional script argument; passing
    // a list of args would make `gradlew` see only the first.
    val script = (listOf("./gradlew") + gradleSubcommand).joinToString(" ")
    return listOf(
        "firebase", "emulators:exec",
        "--project", "demo-moneysurfer",
        "--only", "auth,firestore",
        script,
    )
}

tasks.register<Exec>("qaIntegrationDeviceEmulator") {
    group = "verification"
    description = "Wrap firebase emulators:exec around :integration-test:connectedAndroidDeviceTest. Requires a running AVD."
    workingDir = rootDir
    commandLine(firebaseEmulatorWrap(repoRoot, listOf(":integration-test:connectedAndroidDeviceTest")))
    // Allure runs even on failure — XMLs land at
    // `integration-test/build/outputs/androidTest-results/connected/androidMain/`
    // which `androidDeviceScopeAllureSources` already walks via `resolveXmlDirs`.
    finalizedBy("allureGenerateAndroidDevice")
}

tasks.register<Exec>("qaIntegrationDeviceHermetic") {
    group = "verification"
    description = "Fully hermetic device-IT — boots Firebase emulator + Gradle-Managed AVD, runs :integration-test, tears down."
    workingDir = rootDir
    // Wipe prior test-results so:
    //  1. Gradle's task cache for `integrationAvdAndroidDeviceTest` is invalidated and
    //     the test actually re-executes (otherwise UP-TO-DATE keeps the old run's XML).
    //  2. Allure doesn't merge stale `connected/androidMain/` XMLs (left over from a
    //     manual `connectedAndroidDeviceTest` run on a plugged-in AVD) with the new
    //     `managedDevice/androidmain/integrationAvd/` XMLs — its dedup picks one and
    //     silently drops the other, so a 1-test stale file masks a 2-test fresh run.
    val testResultsDir: File = rootProject.file("integration-test/build/outputs/androidTest-results")
    val managedDeviceExtraDir: File =
        rootProject.file("integration-test/build/outputs/managed_device_android_test_additional_output")
    doFirst {
        // `File.deleteRecursively()` rather than `project.delete(...)`: a task
        // action must not hold on to the `Project`.
        testResultsDir.deleteRecursively()
        managedDeviceExtraDir.deleteRecursively()
    }
    // `integrationAvdAndroidDeviceTest` is the AGP-generated task for the
    // managed device named `integrationAvd` in integration-test/build.gradle.kts.
    // Naming for KMP `KotlinMultiplatformAndroidLibraryTarget`:
    // `<deviceName>AndroidDeviceTest` (note the extra `Device` vs the AGP-app
    // convention of `<deviceName>AndroidTest`).
    commandLine(firebaseEmulatorWrap(repoRoot, listOf(":integration-test:integrationAvdAndroidDeviceTest")))
    // Managed-device XMLs land under `build/outputs/androidTest-results/<deviceName>/`
    // (vs. `connected/androidMain/` for plugged-in devices). Both sit under
    // `androidTest-results/` so the depth-4 walk in `resolveXmlDirs` picks them up.
    finalizedBy("allureGenerateAndroidDevice")
}

/**
 * Aggregate test owners from their source sets instead of maintaining a second
 * module registry here. `TaskCollection` is live, so tasks created later while
 * subprojects are configured are included without `afterEvaluate`.
 *
 * This intentionally keys off source directories: modules with an empty test
 * target do not make every aggregate invocation pay configuration/execution
 * overhead, while a newly added test source set joins QA automatically.
 */
val commonTestTasks = commonTestOwners.map { subproject ->
    subproject.tasks.matching { it.name == "jvmTest" }
}

val androidHostTestTasks = androidHostTestOwners.map { subproject ->
    subproject.tasks.matching { it.name == "testAndroidHostTest" }
}

val androidDeviceTestTasks = listOf(
    ":androidApp:connectedDebugAndroidTest",
    ":shared:connectedAndroidTest",
    ":domain:connectedAndroidTest",
    ":data-local:connectedAndroidTest",
    ":data-remote:connectedAndroidTest",
    ":sync:api:connectedAndroidTest",
    ":sync:default:connectedAndroidTest",
    ":sync-surfer:connectedAndroidTest",
    ":uikit:connectedAndroidTest",
    // `:integration-test` uses the AGP `KotlinMultiplatformAndroidLibraryTarget`
    // which exposes `connectedAndroidDeviceTest` rather than `connectedAndroidTest`.
    // Pre-req: Firebase Emulator Suite (firestore + auth) must be running on the
    // host before this task — see docs/testing/qa-runbook.md "Integration tests".
    ":integration-test:connectedAndroidDeviceTest",
)

tasks.register("testCommon") {
    group = "verification"
    description = "Run common (JVM) test scope across KMP modules."
    dependsOn(commonTestTasks)
}

tasks.register("testAndroidHost") {
    group = "verification"
    description = "Run Android host/unit test scope across KMP modules."
    dependsOn(androidHostTestTasks)
}

tasks.register("testAndroidDevice") {
    group = "verification"
    description = "Run Android device/instrumented test scope (androidApp + KMP android device tests)."
    dependsOn(androidDeviceTestTasks)
}

tasks.register("testAllScopes") {
    group = "verification"
    description = "Run common + Android host + Android device test scopes."
    dependsOn("testCommon", "testAndroidHost", "testAndroidDevice")
}

registerAllureGenerate("allureGenerateCommon", "common (JVM)", commonScopeAllureSources, allureCommonDir)
registerAllureGenerate("allureGenerateAndroidHost", "Android host", androidHostScopeAllureSources, allureAndroidHostDir)
registerAllureGenerate("allureGenerateAndroidDevice", "Android device", androidDeviceScopeAllureSources, allureAndroidDeviceDir)
registerAllureGenerate("allureGenerateMaestro", "Maestro", maestroAllureSources, allureMaestroDir)
registerAllureGenerate("allureGenerateMaestroIos", "Maestro iOS", maestroIosAllureSources, allureMaestroIosDir)
registerAllureGenerate("allureGenerateFirestore", "Firestore rules (Mocha)", firestoreAllureSources, allureFirestoreDir)
registerAllureGenerate("allureGenerateAll", "all", allScopeAllureSources, allureAllDir)

tasks.register<Exec>("maestroPrepareAllureResults") {
    group = "verification"
    description = "Convert Maestro JUnit + debug artifacts into native Allure results with test steps and screenshots."
    // Prep is best-effort: a malformed JUnit or an unreadable attachment must
    // not break the Allure HTML pipeline, otherwise CI uploads an empty report
    // on red runs. We still log the converter's exit code in doLast.
    isIgnoreExitValue = true
    val allureResultsDir = maestroAllureResultsDir
    val reportsDir = maestroReportsDir
    val junit = maestroAllFlowsJunit
    doFirst {
        allureResultsDir.mkdirs()
        reportsDir.mkdirs()
    }
    commandLine(
        "python3",
        rootDir.resolve("scripts/maestro/maestro_to_allure.py").absolutePath,
        "--junit", maestroAllFlowsJunit.absolutePath,
        "--debug-dir", maestroDebugDir.absolutePath,
        "--artifacts-dir", maestroArtifactsDir.absolutePath,
        "--out-dir", maestroAllureResultsDir.absolutePath,
        "--pipeline",
        "qaMaestroAndroid -> maestroInstallDebug -> maestroAssembleDebug -> :androidApp:assembleDebug(-PuseEmulator=true) -> firebase emulators:exec(auth,firestore) -> scripts/firebase/seed.sh -> maestro test --format junit --debug-output --test-output-dir -> maestroPrepareAllureResults -> allureGenerateMaestro",
    )
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            logger.warn(
                "[maestro] maestro_to_allure.py exited with $exit — Allure will render with " +
                    "whatever results were already written. JUnit: ${junit.absolutePath}",
            )
        }
        AllureTools.writeFallbackMetadata(
            outDir = allureResultsDir,
            scope = "qaMaestroAndroid",
            pipeline = "maestroPrepareAllureResults -> allureGenerateMaestro",
        )
    }
}

tasks.named<Exec>("allureGenerateMaestro") {
    dependsOn("maestroPrepareAllureResults")
}

tasks.register<Exec>("maestroPrepareAllureResultsIos") {
    group = "verification"
    description = "Convert iOS Maestro JUnit + debug artifacts into native Allure results with test steps and screenshots."
    isIgnoreExitValue = true
    val allureResultsDir = maestroIosAllureResultsDir
    val reportsDir = maestroIosReportsDir
    val junit = maestroIosAllFlowsJunit
    doFirst {
        allureResultsDir.mkdirs()
        reportsDir.mkdirs()
    }
    commandLine(
        "python3",
        rootDir.resolve("scripts/maestro/maestro_to_allure.py").absolutePath,
        "--junit", maestroIosAllFlowsJunit.absolutePath,
        "--debug-dir", maestroIosDebugDir.absolutePath,
        "--artifacts-dir", maestroIosArtifactsDir.absolutePath,
        "--out-dir", maestroIosAllureResultsDir.absolutePath,
        "--pipeline",
        "qaMaestroIos -> maestroInstallIosSimulator -> maestroBuildIosSimulator -> xcodebuild(Debug iphonesimulator, MS_USE_EMULATOR=YES) -> firebase emulators:exec(auth,firestore) -> scripts/firebase/seed.sh -> maestro test --format junit --debug-output --test-output-dir -> maestroPrepareAllureResultsIos -> allureGenerateMaestroIos",
    )
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            logger.warn(
                "[maestro-ios] maestro_to_allure.py exited with $exit — Allure will render with " +
                    "whatever results were already written. JUnit: ${junit.absolutePath}",
            )
        }
        AllureTools.writeFallbackMetadata(
            outDir = allureResultsDir,
            scope = "qaMaestroIos",
            pipeline = "maestroPrepareAllureResultsIos -> allureGenerateMaestroIos",
        )
    }
}

tasks.named<Exec>("allureGenerateMaestroIos") {
    dependsOn("maestroPrepareAllureResultsIos")
}

tasks.register("qaCommon") {
    group = "verification"
    description = "Run common scope tests + Kover reports; always generate Allure report into build/reports/allure/common/."
    dependsOn("testCommon", "koverXmlReport", "koverHtmlReport")
    finalizedBy("allureGenerateCommon")
}

tasks.register("qaAndroidHost") {
    group = "verification"
    description = "Run android host scope tests + Kover reports; always generate Allure report into build/reports/allure/android-host/."
    dependsOn("testAndroidHost", "koverXmlReport", "koverHtmlReport")
    finalizedBy("allureGenerateAndroidHost")
}

tasks.register("qaAndroidDevice") {
    group = "verification"
    description = "Run android device scope tests; always generate Allure report into build/reports/allure/android-device/."
    dependsOn("testAndroidDevice")
    finalizedBy("allureGenerateAndroidDevice")
}

/**
 * Mocha-based Firestore security rules tests under `firestore-tests/`. The npm
 * `test:junit` script wraps `firebase emulators:exec --only firestore` around
 * `mocha`, writing JUnit XML to `build/test-results/firestore/` (relative path
 * `../build/...` from inside `firestore-tests/`). `mocha-junit-reporter` creates
 * the directory automatically.
 *
 * `isIgnoreExitValue = true` mirrors `maestroRunAllJunit` — we still want the
 * Allure report on red runs, and we re-throw with a clearer message in `doLast`.
 */
tasks.register<Exec>("qaFirestoreRules") {
    group = "verification"
    description = "Run Mocha Firestore-rules tests (boots firestore emulator); writes JUnit XML for Allure into build/test-results/firestore/."
    finalizedBy("allureGenerateFirestore")
    isIgnoreExitValue = true
    workingDir = firestoreTestsDir
    val reportsDir = firestoreReportsDir
    val junit = firestoreJunit
    doFirst {
        reportsDir.mkdirs()
    }
    commandLine("npm", "run", "test:junit")
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            throw GradleException(
                "qaFirestoreRules failed (exit=$exit). JUnit: ${junit.absolutePath}",
            )
        }
    }
}

/**
 * Boots Auth + Firestore emulators → seeds test users → runs all Maestro flows
 * → tears down. Project id pinned to `demo-moneysurfer` (matches
 * `firestore-tests/` and `:integration-test` EmulatorEnv). APK has
 * `BuildConfig.USE_EMULATOR=true` so it talks to `10.0.2.2:8080/9099`.
 */
tasks.register<Exec>("qaMaestroAndroid") {
    group = "verification"
    description = "Boot Firebase Emulator, seed users, run all Android Maestro flows, generate Allure report."
    dependsOn("maestroInstallDebug")
    finalizedBy("allureGenerateMaestro")
    isIgnoreExitValue = true
    workingDir = rootDir
    val flowsDir = repoRoot.resolve("scripts/maestro/").absolutePath
    val reportPath = maestroAllFlowsJunit.absolutePath
    val debugOutputPath = maestroDebugDir.absolutePath
    val testOutputPath = maestroArtifactsDir.absolutePath
    val envArgs = maestroEmulatorEnv.flatMap { (k, v) -> listOf("--env", "$k=$v") }
    val excludedTags = maestroSetupTags
    val reportsDir = maestroReportsDir
    val debugDir = maestroDebugDir
    val artifactsDir = maestroArtifactsDir
    val junit = maestroAllFlowsJunit
    val allureDir = allureMaestroDir
    doFirst {
        reportsDir.mkdirs()
        debugDir.mkdirs()
        artifactsDir.mkdirs()
        // Resolved here, not at configuration time: `File.canExecute()` is not
        // a tracked configuration-cache input, so a path baked into the cache
        // entry would survive installing/moving the Maestro CLI.
        val maestroBin = MaestroTools.resolveMaestroExecutable()
        commandLine(
            listOf(
                "firebase", "emulators:exec",
                "--project", "demo-moneysurfer",
                "--only", "auth,firestore",
            ) + listOf(
                (listOf("scripts/firebase/seed.sh", "&&", maestroBin, "test") + envArgs +
                    listOf(
                        "--format", "junit",
                        "--output", reportPath,
                        "--debug-output", debugOutputPath,
                        "--test-output-dir", testOutputPath,
                        "--flatten-debug-output",
                    ) + excludedTags.flatMap { listOf("--exclude-tags", it) } +
                    listOf(
                        flowsDir,
                    ))
                    .joinToString(" "),
            ),
        )
    }
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            val summary = MaestroTools.summarizeMaestroJunit(junit)
            throw GradleException(
                buildString {
                    appendLine("qaMaestroAndroid failed (exit=$exit). $summary")
                    appendLine("JUnit         : ${junit.absolutePath}")
                    appendLine("Maestro debug : ${debugDir.absolutePath}")
                    appendLine("Maestro shots : ${artifactsDir.absolutePath}")
                    appendLine("Allure HTML   : ${allureDir.absolutePath} (generated unconditionally)")
                    append("If JUnit is missing, the failure happened before any flow finished — ")
                    append("inspect the firebase emulators:exec / seed.sh / maestro stdout above.")
                },
            )
        }
    }
}

tasks.register("qaMaestro") {
    group = "verification"
    description = "Alias for qaMaestroAndroid."
    dependsOn("qaMaestroAndroid")
}

/**
 * Boots Auth + Firestore emulators → seeds test users → runs the iOS Maestro
 * flow(s) against the currently booted iOS Simulator → tears down. The Debug
 * simulator app is built with Info.plist `MS_USE_EMULATOR=YES`, so iOS Firebase
 * uses `localhost:8080/9099`.
 *
 * Scope as of #297: the launch smoke ([iosSmokeFlow]) only — the 17-flow online
 * suite was non-deterministically red on iOS, so this lane was cut back to the
 * one assertion worth acting on. The emulator + seed wrapper stays so restoring
 * the full suite is a one-line change back to `scripts/maestro/` + the tag
 * exclusions; `maestroRunAllIos` still drives every flow locally in the
 * meantime (with the emulator already running).
 */
tasks.register<Exec>("qaMaestroIos") {
    group = "verification"
    description = "Boot Firebase Emulator, seed users, run the iOS launch smoke flow, generate Allure report."
    dependsOn("maestroInstallIosSimulator")
    finalizedBy("allureGenerateMaestroIos")
    isIgnoreExitValue = true
    workingDir = rootDir
    val flowTarget = repoRoot.resolve(iosSmokeFlow).absolutePath
    val reportPath = maestroIosAllFlowsJunit.absolutePath
    val debugOutputPath = maestroIosDebugDir.absolutePath
    val testOutputPath = maestroIosArtifactsDir.absolutePath
    val envArgs = maestroIosEmulatorEnv.flatMap { (k, v) -> listOf("--env", "$k=$v") }
    val deviceId = iosMaestroDeviceId
    val reportsDir = maestroIosReportsDir
    val debugDir = maestroIosDebugDir
    val artifactsDir = maestroIosArtifactsDir
    val junit = maestroIosAllFlowsJunit
    val allureDir = allureMaestroIosDir
    doFirst {
        reportsDir.mkdirs()
        debugDir.mkdirs()
        artifactsDir.mkdirs()
        // See qaMaestroAndroid: the CLI path must not be baked into the
        // configuration-cache entry.
        val maestroBin = MaestroTools.resolveMaestroExecutable()
        commandLine(
            listOf(
                "firebase", "emulators:exec",
                "--project", "demo-moneysurfer",
                "--only", "auth,firestore",
            ) + listOf(
                (listOf("scripts/firebase/seed.sh", "&&", maestroBin, "test") + envArgs +
                    listOf(
                        "--platform", "ios",
                    ) + deviceId?.let { listOf("--device", it) }.orEmpty() +
                    listOf(
                        "--format", "junit",
                        "--output", reportPath,
                        "--debug-output", debugOutputPath,
                        "--test-output-dir", testOutputPath,
                        "--flatten-debug-output",
                    ) +
                    // No `--exclude-tags`: the target is a single flow file rather
                    // than the suite directory, so there is nothing to filter out.
                    listOf(
                        flowTarget,
                    ))
                    .joinToString(" "),
            ),
        )
    }
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            val summary = MaestroTools.summarizeMaestroJunit(junit)
            throw GradleException(
                buildString {
                    appendLine("qaMaestroIos failed (exit=$exit). $summary")
                    appendLine("JUnit         : ${junit.absolutePath}")
                    appendLine("Maestro debug : ${debugDir.absolutePath}")
                    appendLine("Maestro shots : ${artifactsDir.absolutePath}")
                    appendLine("Allure HTML   : ${allureDir.absolutePath} (generated unconditionally)")
                    append("If JUnit is missing, the failure happened before any flow finished — ")
                    append("inspect xcodebuild / firebase emulators:exec / maestro stdout above.")
                },
            )
        }
    }
}

/**
 * Offline-build Maestro golden path (`scripts/maestro/offline/offline-golden.yaml`,
 * tagged `offline`). The offline app makes zero network calls, so — unlike the
 * online suites — these tasks need no Firebase emulator and no seeded test users.
 * They install the `.dev` debug offline binary and run only the `offline`-tagged
 * flow via `--include-tags`.
 *
 * iOS is the exception while #297 is open: `qaMaestroOfflineIos` drives the launch
 * smoke ([iosSmokeFlow]) against the same offline binary instead of the golden
 * path, which keeps running on Android via `qaMaestroOfflineAndroid`.
 */
val offlineDebugApkPath = rootProject.file(
    "androidApp-offline/build/outputs/apk/debug/androidApp-offline-debug.apk",
)
val iosOfflineMaestroDerivedDataDir = rootProject.file("build/ios-maestro-offline")
// The offline Xcode config (`iosAppOffline/Configuration/Config.xcconfig`) overrides
// `PRODUCT_NAME` to `MoneySurferOffline Dev` for the Debug configuration, so the
// `-configuration Debug` build emits a bundle under that (space-containing) name.
val iosOfflineMaestroAppPath = iosOfflineMaestroDerivedDataDir.resolve(
    "Build/Products/Debug-iphonesimulator/MoneySurferOffline Dev.app",
)
val maestroOfflineJunit = maestroReportsDir.resolve("maestro-offline-golden.xml")
// iOS runs the launch smoke instead of the golden path (#297) — hence the
// different report name from the Android one right above.
val maestroOfflineIosJunit = maestroIosReportsDir.resolve("maestro-offline-app-open-ios.xml")

tasks.register("maestroAssembleOfflineDebug") {
    group = "verification"
    description = "Build the offline debug APK for Maestro E2E tests."
    dependsOn(":androidApp-offline:assembleDebug")
}

tasks.register<Exec>("maestroInstallOfflineDebug") {
    group = "verification"
    description = "Build the offline debug APK and adb-install it on the connected device/AVD."
    dependsOn("maestroAssembleOfflineDebug")
    val root = repoRoot
    val apk = offlineDebugApkPath
    doFirst {
        require(apk.exists()) {
            "Offline debug APK not found at ${apk.absolutePath}. " +
                ":androidApp-offline:assembleDebug did not produce its expected output — " +
                "check the assembleDebug log above."
        }
        commandLine(MaestroTools.resolveAdbExecutable(root), "install", "-r", apk.absolutePath)
    }
}

tasks.register<Exec>("qaMaestroOfflineAndroid") {
    group = "verification"
    description = "Install the offline app + run the offline golden Maestro flow on Android (no Firebase emulator)."
    dependsOn("maestroInstallOfflineDebug")
    workingDir = rootDir
    val root = repoRoot
    val reportsDir = maestroReportsDir
    val junit = maestroOfflineJunit
    val appId = offlineMaestroAppId
    doFirst {
        reportsDir.mkdirs()
        commandLine(
            MaestroTools.buildMaestroCommand(
                rootDir = root,
                // Target the subdirectory itself: without a workspace config
                // Maestro only scans top-level flows, so `scripts/maestro/`
                // never discovers `offline/offline-golden.yaml` and
                // `--include-tags offline` matches nothing.
                target = "scripts/maestro/offline/",
                junitOutput = junit,
                includeTags = listOf("offline"),
                appId = appId,
            ),
        )
    }
}

tasks.register<Exec>("maestroBuildIosOfflineSimulator") {
    group = "verification"
    description = "Build the iOS offline Debug simulator app for Maestro E2E tests."
    workingDir = rootDir
    val simulatorName = providers.gradleProperty("iosSimulatorName").orNull
        ?: System.getenv("IOS_SIMULATOR_NAME")
        ?: "iPhone 17"
    commandLine(
        "xcodebuild",
        "-project", "iosAppOffline/iosAppOffline.xcodeproj",
        "-scheme", "iosAppOffline",
        "-configuration", "Debug",
        "-sdk", "iphonesimulator",
        "-destination", "platform=iOS Simulator,name=$simulatorName",
        "-derivedDataPath", iosOfflineMaestroDerivedDataDir.absolutePath,
        "build",
    )
}

tasks.register<Exec>("maestroInstallIosOfflineSimulator") {
    group = "verification"
    description = "Build and install the iOS offline simulator app on the booted Simulator."
    dependsOn("maestroBuildIosOfflineSimulator")
    val appPath = iosOfflineMaestroAppPath
    doFirst {
        require(appPath.exists()) {
            "iOS offline app not found at ${appPath.absolutePath}. " +
                "Run maestroBuildIosOfflineSimulator first."
        }
        commandLine("xcrun", "simctl", "install", "booted", appPath.absolutePath)
    }
}

tasks.register<Exec>("qaMaestroOfflineIos") {
    group = "verification"
    description = "Install the offline app + run the launch smoke Maestro flow on the booted iOS Simulator."
    dependsOn("maestroInstallIosOfflineSimulator")
    workingDir = rootDir
    val root = repoRoot
    val reportsDir = maestroIosReportsDir
    val junit = maestroOfflineIosJunit
    val appId = offlineMaestroAppId
    val smokeFlow = iosSmokeFlow
    val deviceId = iosMaestroDeviceId
    doFirst {
        reportsDir.mkdirs()
        commandLine(
            MaestroTools.buildMaestroCommand(
                rootDir = root,
                // Launch smoke instead of the offline golden path while the iOS
                // lanes are cut back (#297) — the golden flow still runs on
                // Android via `qaMaestroOfflineAndroid`. Single-file target, so
                // no `--include-tags offline` filter is needed; the flow picks up
                // the offline bundle id from `APP_ID` below.
                target = smokeFlow,
                junitOutput = junit,
                appId = appId,
                platform = "ios",
                deviceId = deviceId,
            ),
        )
    }
}

tasks.register("qaJvmAndAndroid") {
    group = "verification"
    description = "Run JVM, Android host, and Android device scopes + Kover; generate the combined Allure report."
    dependsOn("testAllScopes", "koverXmlReport", "koverHtmlReport")
    finalizedBy("allureGenerateAll", "printKoverCoverageImportHints")
}

tasks.register("qaAll") {
    group = "verification"
    description = "Deprecated alias for qaJvmAndAndroid; does not run Maestro or Firestore-rules tests."
    dependsOn("qaJvmAndAndroid")
}

tasks.register("printKoverCoverageImportHints") {
    group = "verification"
    description = "Print Kover .ic paths for Android Studio (Run > Show Coverage Data...) + HTML report path."
    val binReportDirs: List<File> = subprojects.map { sp ->
        sp.layout.buildDirectory.dir("kover/bin-reports").get().asFile
    }
    val htmlIndex: File = rootProject.layout.buildDirectory.file("reports/kover/html/index.html").get().asFile
    doLast {
        val icFiles = binReportDirs
            .filter(File::isDirectory)
            .flatMap { dir -> dir.listFiles { _, name -> name.endsWith(".ic") }?.toList().orEmpty() }
            .sortedBy { it.absolutePath }

        logger.lifecycle("")
        logger.lifecycle("Kover coverage:")
        logger.lifecycle("  HTML : file://${htmlIndex.absolutePath}")
        logger.lifecycle("  AS import (Run > Show Coverage Data... → +):")
        if (icFiles.isEmpty()) {
            logger.lifecycle("    (no .ic files — run tests first)")
        } else {
            icFiles.forEach { logger.lifecycle("    ${it.absolutePath}") }
        }
    }
}
