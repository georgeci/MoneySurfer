package com.georgeci.moneysurfer.buildlogic

import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Execution-time helpers for the Maestro/adb QA tasks registered in
 * `gradle/qa.gradle.kts`.
 *
 * These used to be top-level functions of that script. A `doFirst` / `doLast`
 * lambda that calls a script-level function captures the compiled script
 * instance, which the configuration cache cannot serialize — every Maestro run
 * ended with "Configuration cache entry discarded with N problems", even though
 * the tasks declared `notCompatibleWithConfigurationCache(...)` (the opt-out
 * suppresses the *incompatibility*, not the serialization report).
 *
 * Living here — on the plugin classpath of the `build-logic` included build —
 * they are a plain singleton on the task action's classpath instead, so the
 * actions capture nothing but `File`s and `String`s.
 *
 * Everything is deliberately `Project`-free: callers pass the repo root as a
 * `File` captured at configuration time.
 */
object MaestroTools {

    const val ANDROID_APP_ID: String = "com.georgeci.moneysurfer.dev"

    // iOS Debug mirrors the Android `.dev` flavor (project.pbxproj sets
    // PRODUCT_BUNDLE_IDENTIFIER=com.georgeci.moneysurfer.dev for the Debug config),
    // so the installed simulator app — the one qaMaestroIos builds — carries the
    // `.dev` suffix too. Launching the un-suffixed id fails every flow with
    // "Unable to launch app com.georgeci.moneysurfer".
    const val IOS_APP_ID: String = "com.georgeci.moneysurfer.dev"

    // Offline build, debug variant: `KmpAppConventionPlugin` appends `.dev` to the
    // `com.georgeci.moneysurfer.offline` applicationId / bundle id. Android and iOS
    // debug builds land on the same id, so a single constant covers both.
    const val OFFLINE_APP_ID: String = "com.georgeci.moneysurfer.offline.dev"

    private const val DEFAULT_LOG_TAIL_LINES = 40

    private const val MAX_FAILED_NAMES_SHOWN = 3

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
            val props = Properties()
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

    /**
     * Resolves the `maestro` CLI. Same problem as adb — Gradle daemon's PATH
     * skips Homebrew. Returns absolute path; caller falls back to bare `"maestro"`
     * if nothing found, so CI images that put it on PATH still work.
     */
    fun resolveMaestroExecutable(): String {
        System.getenv("MAESTRO_BIN")?.takeIf { it.isNotBlank() && File(it).canExecute() }?.let { return it }
        val candidates = listOfNotNull(
            "/opt/homebrew/bin/maestro",
            "/usr/local/bin/maestro",
            System.getenv("HOME")?.let { "$it/.maestro/bin/maestro" },
        )
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

    fun loadMaestroTestUser(rootDir: File): Map<String, String> =
        loadKeyValueFile(rootDir.resolve("scripts/e2e-test-user.properties"))
            .filterKeys { it in setOf("TEST_EMAIL", "TEST_PASSWORD") }

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

    @Suppress("LongParameterList")
    fun buildMaestroCommand(
        rootDir: File,
        target: String,
        junitOutput: File? = null,
        excludeTags: List<String> = emptyList(),
        includeTags: List<String> = emptyList(),
        appId: String = ANDROID_APP_ID,
        platform: String? = null,
        deviceId: String? = null,
    ): List<String> {
        val command = mutableListOf(resolveMaestroExecutable(), "test")
        if (!platform.isNullOrBlank()) {
            command += listOf("--platform", platform)
        }
        if (!deviceId.isNullOrBlank()) {
            command += listOf("--device", deviceId)
        }
        val env = loadMaestroTestUser(rootDir) + mapOf("APP_ID" to appId)
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
        includeTags.forEach { tag ->
            command += listOf("--include-tags", tag)
        }
        excludeTags.forEach { tag ->
            command += listOf("--exclude-tags", tag)
        }
        command += target
        return command
    }

    /**
     * Returns the last [lines] lines of [file] for inclusion in failure messages,
     * or "" when the file is missing/empty/unreadable. Maestro/Firebase emulator
     * failures often happen long before JUnit is written, so the only signal is
     * the captured stdout/stderr.
     *
     * Paths are printed relative to [rootDir] — the caller passes the repo root
     * instead of the helper reaching for `rootProject`.
     *
     * Uses `useLines` + a fixed-size ArrayDeque ring buffer so a multi-MB Maestro
     * log doesn't get fully materialised into memory at the moment we're already
     * crashing.
     */
    fun tailLogFile(file: File, rootDir: File, lines: Int = DEFAULT_LOG_TAIL_LINES): String {
        if (!file.exists() || file.length() == 0L) return ""
        return runCatching {
            val buffer = ArrayDeque<String>(lines)
            file.useLines { seq ->
                seq.forEach { line ->
                    if (buffer.size == lines) buffer.removeFirst()
                    buffer.addLast(line)
                }
            }
            if (buffer.isEmpty()) {
                ""
            } else {
                val rel = file.relativeToOrSelf(rootDir)
                "Last ${buffer.size} lines of $rel:\n${buffer.joinToString("\n")}"
            }
        }.getOrElse { "(unable to read ${file.absolutePath}: ${it.message})" }
    }

    /**
     * Joins per-stream tails for a failure message. Includes whichever streams
     * actually produced output — stderr is shown first because it usually carries
     * the cause; stdout follows when present.
     */
    fun joinLogTails(rootDir: File, vararg files: File): String =
        files.map { tailLogFile(it, rootDir) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifBlank { "(no log output captured)" }

    @Suppress("CyclomaticComplexMethod")
    fun summarizeMaestroJunit(report: File): String {
        if (!report.exists()) {
            return "Maestro result unavailable: JUnit report was not written at ${report.absolutePath} " +
                "(Maestro likely crashed before any flow finished — check the emulator/seed logs above)."
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
                val shown = failedNames.take(MAX_FAILED_NAMES_SHOWN).joinToString(", ")
                val more = if (failedNames.size > MAX_FAILED_NAMES_SHOWN) {
                    ", +${failedNames.size - MAX_FAILED_NAMES_SHOWN} more"
                } else {
                    ""
                }
                ". Failed: $shown$more"
            } else {
                "."
            }

            "Maestro result: $passed/$tests passed, $failed failed$skippedText$failedText"
        }.getOrElse { error ->
            "Maestro result unavailable: failed to parse JUnit report (${error.message})."
        }
    }
}
