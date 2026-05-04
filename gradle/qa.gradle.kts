// QA pipeline: tests + Kover + Allure for common, Android host, Android device, Maestro.
// Loaded from root build.gradle.kts via `apply(from = "gradle/qa.gradle.kts")`.

import org.gradle.api.tasks.Exec
import java.io.FileOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Resolves the Android SDK's `adb` binary. The Gradle daemon's PATH does not
 * include shell-only entries (e.g. Homebrew under `/opt/homebrew/bin`), so
 * `commandLine("adb", ...)` fails on macOS unless adb is on the inherited PATH.
 *
 * Resolution order:
 *   1. `local.properties` → `sdk.dir`
 *   2. env `ANDROID_HOME`
 *   3. env `ANDROID_SDK_ROOT`
 */
fun resolveAdbExecutable(rootDir: File): String {
    val sdkDir: String = run {
        val props = java.util.Properties()
        rootDir.resolve("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }
        props.getProperty("sdk.dir")
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: error("Android SDK not found: set `sdk.dir` in local.properties or ANDROID_HOME / ANDROID_SDK_ROOT.")
    }
    val adb = File(sdkDir, "platform-tools/adb")
    require(adb.canExecute()) { "adb not executable at ${adb.absolutePath}. Install platform-tools." }
    return adb.absolutePath
}

fun resolveAllureExecutable(): String {
    System.getenv("ALLURE_BIN")?.takeIf { it.isNotBlank() && File(it).canExecute() }?.let { return it }
    val candidates = listOf(
        "/opt/homebrew/bin/allure",
        "/usr/local/bin/allure",
        "/usr/bin/allure",
    )
    return candidates.firstOrNull { File(it).canExecute() } ?: "allure"
}

/**
 * Resolves the `maestro` CLI. Same problem as adb — Gradle daemon's PATH
 * skips Homebrew. Returns absolute path; caller falls back to bare `"maestro"`
 * if nothing found, so CI images that put it on PATH still work.
 */
fun resolveMaestroExecutable(): String {
    System.getenv("MAESTRO_BIN")?.takeIf { it.isNotBlank() && File(it).canExecute() }?.let { return it }
    val candidates = listOf(
        "/opt/homebrew/bin/maestro",
        "/usr/local/bin/maestro",
        System.getenv("HOME")?.let { "$it/.maestro/bin/maestro" },
    ).filterNotNull()
    return candidates.firstOrNull { File(it).canExecute() } ?: "maestro"
}

fun loadKeyValueFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()

    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val (key, value) = line.split('=', limit = 2)
            key.trim() to value.trim()
        }
}

fun resolveMaestroFlow(flowProperty: String?): String {
    val flow = flowProperty?.trim().orEmpty()
    require(flow.isNotBlank()) {
        "Missing flow. Use -PmaestroFlow=05_sign_out.yaml (or full path)."
    }
    return when {
        flow.startsWith("/") -> flow
        flow.startsWith("scripts/maestro/") -> flow
        else -> "scripts/maestro/$flow"
    }
}

fun loadMaestroTestUser(rootDir: File): Map<String, String> =
    loadKeyValueFile(rootDir.resolve("scripts/e2e-test-user.properties"))
        .filterKeys { it in setOf("TEST_EMAIL", "TEST_PASSWORD") }

fun buildMaestroCommand(
    rootDir: File,
    target: String,
    junitOutput: File? = null,
    excludeTags: List<String> = emptyList(),
): List<String> {
    val command = mutableListOf(resolveMaestroExecutable(), "test")
    val env = loadMaestroTestUser(rootDir)
    env.forEach { (key, value) ->
        command += listOf("--env", "$key=$value")
    }
    if (junitOutput != null) {
        command += listOf("--format", "junit", "--output", junitOutput.absolutePath)
    }
    // Keep debug artifacts/screenshots under project build/ instead of ~/.maestro.
    val debugOutputDir = rootDir.resolve("build/maestro-debug")
    val testOutputDir = rootDir.resolve("build/maestro-artifacts")
    command += listOf(
        "--debug-output", debugOutputDir.absolutePath,
        "--test-output-dir", testOutputDir.absolutePath,
        "--flatten-debug-output",
    )
    excludeTags.forEach { tag ->
        command += listOf("--exclude-tags", tag)
    }
    command += target
    return command
}

fun summarizeMaestroJunit(report: File): String {
    if (!report.exists()) {
        return "Maestro result unavailable: JUnit report was not created."
    }

    return runCatching {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(report)

        val suites = document.getElementsByTagName("testsuite")
        var tests = 0
        var failures = 0
        var errors = 0
        var skipped = 0

        for (index in 0 until suites.length) {
            val suite = suites.item(index) as Element
            tests += suite.getAttribute("tests").toIntOrNull() ?: 0
            failures += suite.getAttribute("failures").toIntOrNull() ?: 0
            errors += suite.getAttribute("errors").toIntOrNull() ?: 0
            skipped += suite.getAttribute("skipped").toIntOrNull() ?: 0
        }

        val failedNames = mutableListOf<String>()
        val cases = document.getElementsByTagName("testcase")
        for (index in 0 until cases.length) {
            val testcase = cases.item(index) as Element
            val hasFailure = testcase.getElementsByTagName("failure").length > 0
            val hasError = testcase.getElementsByTagName("error").length > 0
            val status = testcase.getAttribute("status")
            if (hasFailure || hasError || status.equals("ERROR", ignoreCase = true)) {
                failedNames += testcase.getAttribute("name").ifBlank { testcase.getAttribute("id") }
            }
        }

        if (tests == 0) {
            tests = cases.length
        }

        val failed = maxOf(failures + errors, failedNames.size)
        val passed = (tests - failed - skipped).coerceAtLeast(0)
        val skippedText = if (skipped > 0) ", $skipped skipped" else ""
        val failedText = if (failedNames.isNotEmpty()) {
            val shown = failedNames.take(3).joinToString(", ")
            val more = if (failedNames.size > 3) ", +${failedNames.size - 3} more" else ""
            ". Failed: $shown$more"
        } else {
            "."
        }

        "Maestro result: $passed/$tests passed, $failed failed$skippedText$failedText"
    }.getOrElse { error ->
        "Maestro result unavailable: failed to parse JUnit report (${error.message})."
    }
}

val debugApkPath = rootProject.file("androidApp/build/outputs/apk/debug/androidApp-debug.apk")
val maestroReportsDir = rootProject.file("build/test-results/maestro")
val maestroAllFlowsJunit = maestroReportsDir.resolve("maestro-report.xml")
val maestroLogsDir = rootProject.file("build/logs/maestro")
val maestroDebugDir = rootProject.file("build/maestro-debug")
val maestroArtifactsDir = rootProject.file("build/maestro-artifacts")
val maestroAllureResultsDir = rootProject.file("build/allure-results/maestro")
val maestroEmulatorEnv = loadMaestroTestUser(rootDir)
val maestroSetupTags = listOf("setup")

val allureRootDir = rootProject.file("build/reports/allure")
val allureCommonDir = allureRootDir.resolve("common")
val allureAndroidHostDir = allureRootDir.resolve("android-host")
val allureAndroidDeviceDir = allureRootDir.resolve("android-device")
val allureMaestroDir = allureRootDir.resolve("maestro")
val allureAllDir = allureRootDir.resolve("all")

val commonScopeModules = listOf("composeApp", "shared", "domain", "data", "sync", "integration-test")
val androidHostScopeModules = listOf("shared", "domain", "data", "sync", "uikit")
val androidDeviceScopeModules =
    listOf("androidApp", "shared", "domain", "data", "sync", "uikit", "integration-test")

fun moduleTestResults(module: String, scope: String): File =
    rootProject.file("$module/build/test-results/$scope")

fun moduleAndroidDeviceResults(module: String): File =
    rootProject.file("$module/build/outputs/androidTest-results")

val commonScopeAllureSources: List<File> =
    commonScopeModules.map { moduleTestResults(it, "jvmTest") }

val androidHostScopeAllureSources: List<File> =
    androidHostScopeModules.map { moduleTestResults(it, "testAndroidHostTest") }

val androidDeviceScopeAllureSources: List<File> =
    androidDeviceScopeModules.map(::moduleAndroidDeviceResults)

val maestroAllureSources: List<File> = listOf(maestroAllureResultsDir)

val allScopeAllureSources: List<File> =
    (commonScopeAllureSources + androidHostScopeAllureSources + androidDeviceScopeAllureSources + maestroAllureSources)
        .distinct()

/**
 * Walks each source directory and returns every subdir (and the source itself)
 * that holds at least one supported Allure input file directly inside it.
 * Inputs can be JUnit XML (`*.xml`) or native Allure files
 * (`*-result.json`, `executor.json`, `environment.properties`).
 *
 * Used to feed `allure generate` paths at multiple depths in one call:
 * JVM/host tests put XMLs at the root of the source dir, KMP device tests put
 * them at `connected/androidMain/`, AGP-app device tests at
 * `connected<Flavor>/`, and Maestro enriched results live under
 * `build/allure-results/maestro/` as native JSON + metadata.
 *
 * Cap at depth 4 so a stray walk doesn't descend into `build/intermediates/`.
 */
fun resolveAllureInputDirs(sources: List<File>): List<File> {
    val out = mutableListOf<File>()
    val containsSupportedInputs: (File) -> Boolean = { dir ->
        dir.isDirectory && dir.listFiles { _, name ->
            name.endsWith(".xml") ||
                name.endsWith("-result.json") ||
                name == "executor.json" ||
                name == "environment.properties"
        }?.isNotEmpty() == true
    }
    sources.forEach { source ->
        if (!source.exists()) return@forEach
        if (containsSupportedInputs(source)) out += source
        source.walkTopDown()
            .maxDepth(4)
            .filter { it != source && containsSupportedInputs(it) }
            .forEach { out += it }
    }
    return out.distinct()
}

fun registerAllureGenerate(name: String, scopeLabel: String, sources: List<File>, output: File) {
    tasks.register<Exec>(name) {
        group = "verification"
        description = "Generate Allure report for $scopeLabel scope into ${output.relativeTo(rootProject.projectDir)}."
        val args = mutableListOf<String>(resolveAllureExecutable(), "generate")
        resolveAllureInputDirs(sources).forEach { args += it.absolutePath }
        args += listOf("-o", output.absolutePath, "--clean")
        commandLine(args)
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
    commandLine(resolveAdbExecutable(rootDir), "install", "-r", debugApkPath.absolutePath)
}

tasks.register<Exec>("maestroRunAll") {
    group = "verification"
    description = "Install APK + run all Maestro flows (Firebase Emulator must be running)."
    dependsOn("maestroInstallDebug")
    commandLine(buildMaestroCommand(rootDir, "scripts/maestro/", excludeTags = maestroSetupTags))
}

tasks.register<Exec>("maestroRunOne") {
    group = "verification"
    description = "Install APK + run one Maestro flow (Firebase Emulator must be running). Pass -PmaestroFlow=05_sign_out.yaml."
    notCompatibleWithConfigurationCache("Flow path is resolved dynamically at execution time.")
    dependsOn("maestroInstallDebug")
    doFirst {
        val flow = resolveMaestroFlow(providers.gradleProperty("maestroFlow").orNull)
        commandLine(buildMaestroCommand(rootDir, flow))
    }
}

tasks.register<Exec>("maestroRunAllJunit") {
    group = "verification"
    description = "Install APK, run all flows (Firebase Emulator must be running), write JUnit XML to build/test-results/maestro/maestro-report.xml."
    notCompatibleWithConfigurationCache("Wires dynamic log streams in doFirst.")
    dependsOn("maestroInstallDebug")
    finalizedBy("allureGenerateMaestro")
    isIgnoreExitValue = true
    val stdoutLog = maestroLogsDir.resolve("maestroRunAllJunit.out.log")
    val stderrLog = maestroLogsDir.resolve("maestroRunAllJunit.err.log")
    doFirst {
        maestroReportsDir.mkdirs()
        maestroLogsDir.mkdirs()
        maestroDebugDir.mkdirs()
        maestroArtifactsDir.mkdirs()
        standardOutput = FileOutputStream(stdoutLog)
        errorOutput = FileOutputStream(stderrLog)
        logger.lifecycle("[maestro] junit: ${maestroAllFlowsJunit.relativeTo(rootProject.projectDir)}")
        logger.lifecycle("[maestro] logs : ${stdoutLog.relativeTo(rootProject.projectDir)}, ${stderrLog.relativeTo(rootProject.projectDir)}")
    }
    commandLine(buildMaestroCommand(rootDir, "scripts/maestro/", maestroAllFlowsJunit, excludeTags = maestroSetupTags))
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            val summary = summarizeMaestroJunit(maestroAllFlowsJunit)
            throw GradleException(
                "maestroRunAllJunit failed (exit=$exit). $summary Logs: ${stdoutLog.absolutePath} and ${stderrLog.absolutePath}",
            )
        }
    }
}

tasks.register<Exec>("maestroRunOneJunit") {
    group = "verification"
    description = "Install APK, run one flow (Firebase Emulator must be running), write JUnit XML. Pass -PmaestroFlow=05_sign_out.yaml."
    notCompatibleWithConfigurationCache("Flow path is resolved dynamically at execution time.")
    dependsOn("maestroInstallDebug")
    finalizedBy("allureGenerateMaestro")
    doFirst {
        maestroReportsDir.mkdirs()
        maestroDebugDir.mkdirs()
        maestroArtifactsDir.mkdirs()
        val flow = resolveMaestroFlow(providers.gradleProperty("maestroFlow").orNull)
        val flowName = flow.substringAfterLast('/').substringBeforeLast('.')
        val reportFile = maestroReportsDir.resolve("maestro-$flowName.xml")
        commandLine(buildMaestroCommand(rootDir, flow, reportFile))
    }
}

tasks.register("maestroRun") {
    group = "verification"
    description = "Alias for maestroRunAll."
    dependsOn("maestroRunAll")
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
    notCompatibleWithConfigurationCache("Spawns Firebase emulator subprocess.")
    workingDir = rootDir
    commandLine(firebaseEmulatorWrap(rootDir, listOf(":integration-test:connectedAndroidDeviceTest")))
    // Allure runs even on failure — XMLs land at
    // `integration-test/build/outputs/androidTest-results/connected/androidMain/`
    // which `androidDeviceScopeAllureSources` already walks via `resolveXmlDirs`.
    finalizedBy("allureGenerateAndroidDevice")
}

tasks.register<Exec>("qaIntegrationDeviceHermetic") {
    group = "verification"
    description = "Fully hermetic device-IT — boots Firebase emulator + Gradle-Managed AVD, runs :integration-test, tears down."
    notCompatibleWithConfigurationCache("Spawns Firebase emulator subprocess.")
    workingDir = rootDir
    // Wipe prior test-results so:
    //  1. Gradle's task cache for `integrationAvdAndroidDeviceTest` is invalidated and
    //     the test actually re-executes (otherwise UP-TO-DATE keeps the old run's XML).
    //  2. Allure doesn't merge stale `connected/androidMain/` XMLs (left over from a
    //     manual `connectedAndroidDeviceTest` run on a plugged-in AVD) with the new
    //     `managedDevice/androidmain/integrationAvd/` XMLs — its dedup picks one and
    //     silently drops the other, so a 1-test stale file masks a 2-test fresh run.
    val testResultsDir = rootProject.file("integration-test/build/outputs/androidTest-results")
    val managedDeviceExtraDir = rootProject.file("integration-test/build/outputs/managed_device_android_test_additional_output")
    doFirst {
        project.delete(testResultsDir, managedDeviceExtraDir)
    }
    // `integrationAvdAndroidDeviceTest` is the AGP-generated task for the
    // managed device named `integrationAvd` in integration-test/build.gradle.kts.
    // Naming for KMP `KotlinMultiplatformAndroidLibraryTarget`:
    // `<deviceName>AndroidDeviceTest` (note the extra `Device` vs the AGP-app
    // convention of `<deviceName>AndroidTest`).
    commandLine(firebaseEmulatorWrap(rootDir, listOf(":integration-test:integrationAvdAndroidDeviceTest")))
    // Managed-device XMLs land under `build/outputs/androidTest-results/<deviceName>/`
    // (vs. `connected/androidMain/` for plugged-in devices). Both sit under
    // `androidTest-results/` so the depth-4 walk in `resolveXmlDirs` picks them up.
    finalizedBy("allureGenerateAndroidDevice")
}

val commonTestTasks = listOf(
    ":composeApp:jvmTest",
    ":shared:jvmTest",
    ":domain:jvmTest",
    ":data:jvmTest",
    ":sync:jvmTest",
    ":integration-test:jvmTest",
)

val androidHostTestTasks = listOf(
    ":shared:testAndroidHostTest",
    ":domain:testAndroidHostTest",
    ":data:testAndroidHostTest",
    ":sync:testAndroidHostTest",
    ":uikit:testAndroidHostTest",
)

val androidDeviceTestTasks = listOf(
    ":androidApp:connectedDebugAndroidTest",
    ":shared:connectedAndroidTest",
    ":domain:connectedAndroidTest",
    ":data:connectedAndroidTest",
    ":sync:connectedAndroidTest",
    ":uikit:connectedAndroidTest",
    // `:integration-test` uses the AGP `KotlinMultiplatformAndroidLibraryTarget`
    // which exposes `connectedAndroidDeviceTest` rather than `connectedAndroidTest`.
    // Pre-req: Firebase Emulator Suite (firestore + auth) must be running on the
    // host before this task — see README_TEST.md "Integration tests" section.
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
registerAllureGenerate("allureGenerateAll", "all", allScopeAllureSources, allureAllDir)

tasks.register<Exec>("maestroPrepareAllureResults") {
    group = "verification"
    description = "Convert Maestro JUnit + debug artifacts into native Allure results with test steps and screenshots."
    notCompatibleWithConfigurationCache("Produces dynamic files from test artifacts.")
    doFirst {
        maestroAllureResultsDir.mkdirs()
        maestroReportsDir.mkdirs()
    }
    commandLine(
        "python3",
        rootDir.resolve("scripts/maestro/maestro_to_allure.py").absolutePath,
        "--junit", maestroAllFlowsJunit.absolutePath,
        "--debug-dir", maestroDebugDir.absolutePath,
        "--artifacts-dir", maestroArtifactsDir.absolutePath,
        "--out-dir", maestroAllureResultsDir.absolutePath,
        "--pipeline",
        "qaMaestro -> maestroInstallDebug -> maestroAssembleDebug -> :androidApp:assembleDebug(-PuseEmulator=true) -> firebase emulators:exec(auth,firestore) -> scripts/firebase/seed.sh -> maestro test --format junit --debug-output --test-output-dir -> maestroPrepareAllureResults -> allureGenerateMaestro",
    )
}

tasks.named<Exec>("allureGenerateMaestro") {
    dependsOn("maestroPrepareAllureResults")
}

tasks.named<Exec>("allureGenerateAll") {
    dependsOn("maestroPrepareAllureResults")
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
 * Boots Auth + Firestore emulators → seeds test users → runs all Maestro flows
 * → tears down. Project id pinned to `demo-moneysurfer` (matches
 * `firestore-tests/` and `:integration-test` EmulatorEnv). APK has
 * `BuildConfig.USE_EMULATOR=true` so it talks to `10.0.2.2:8080/9099`.
 */
tasks.register<Exec>("qaMaestro") {
    group = "verification"
    description = "Boot Firebase Emulator, seed users, run all Maestro flows, generate Allure report."
    notCompatibleWithConfigurationCache("Spawns Firebase emulator subprocess.")
    dependsOn("maestroInstallDebug")
    finalizedBy("allureGenerateMaestro")
    isIgnoreExitValue = true
    workingDir = rootDir
    val maestroBin = resolveMaestroExecutable()
    val flowsDir = rootDir.resolve("scripts/maestro/").absolutePath
    val reportPath = maestroAllFlowsJunit.absolutePath
    val debugOutputPath = maestroDebugDir.absolutePath
    val testOutputPath = maestroArtifactsDir.absolutePath
    val envArgs = maestroEmulatorEnv.flatMap { (k, v) -> listOf("--env", "$k=$v") }
    doFirst {
        maestroReportsDir.mkdirs()
        maestroDebugDir.mkdirs()
        maestroArtifactsDir.mkdirs()
    }
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
                    "--exclude-tags", "setup",
                    flowsDir,
                ))
                .joinToString(" "),
        ),
    )
    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            val summary = summarizeMaestroJunit(maestroAllFlowsJunit)
            throw GradleException(
                "qaMaestro failed (exit=$exit). $summary JUnit: ${maestroAllFlowsJunit.absolutePath}",
            )
        }
    }
}

tasks.register("qaAll") {
    group = "verification"
    description = "Run all test scopes + Kover reports; always generate Allure report into build/reports/allure/all/."
    dependsOn("testAllScopes", "koverXmlReport", "koverHtmlReport")
    finalizedBy("allureGenerateAll", "printKoverCoverageImportHints")
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
