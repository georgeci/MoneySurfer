package com.georgeci.moneysurfer.buildlogic

import java.io.File

/**
 * Allure-side counterpart of [MaestroTools] — the helpers the `allureGenerate*`
 * and `maestroPrepareAllureResults*` task actions call. Same rationale: a task
 * action must not reach back into `gradle/qa.gradle.kts` or the configuration
 * cache cannot serialize it.
 */
object AllureTools {

    private const val MAX_INPUT_WALK_DEPTH = 4

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
     *
     * Called from the task action, never at configuration time: the directories
     * are written by the very test tasks this report aggregates, so a set of
     * paths baked into a configuration-cache entry would go stale on the next run.
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
                .maxDepth(MAX_INPUT_WALK_DEPTH)
                .filter { it != source && containsSupportedInputs(it) }
                .forEach { out += it }
        }
        return out.distinct()
    }

    /**
     * Builds the full `allure generate` command line for [sources] into [output].
     */
    fun generateCommand(sources: List<File>, output: File): List<String> {
        val args = mutableListOf(resolveAllureExecutable(), "generate")
        resolveAllureInputDirs(sources).forEach { args += it.absolutePath }
        args += listOf("-o", output.absolutePath, "--clean")
        return args
    }

    /**
     * Writes a minimal `executor.json` + `environment.properties` so
     * `allure generate` always has metadata to render, even when the python
     * converter crashed (malformed JUnit, unreadable attachment, etc.). Called
     * unconditionally in `doLast` of the prep tasks.
     */
    fun writeFallbackMetadata(outDir: File, scope: String, pipeline: String) {
        outDir.mkdirs()
        val executor = outDir.resolve("executor.json")
        if (!executor.exists()) {
            executor.writeText(
                """{"name":"Gradle $scope","type":"local","buildName":"$scope",""" +
                    """"buildUrl":"./gradlew $scope","reportName":"Maestro E2E ($scope)"}""",
            )
        }
        val env = outDir.resolve("environment.properties")
        if (!env.exists()) {
            env.writeText(
                """
                firebase.project=demo-moneysurfer
                firebase.mode=emulator
                qa.scope=$scope
                qa.pipeline=$pipeline
                """.trimIndent() + "\n",
            )
        }
    }
}
