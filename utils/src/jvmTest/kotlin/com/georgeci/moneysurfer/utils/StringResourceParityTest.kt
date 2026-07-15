package com.georgeci.moneysurfer.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Repo-wide gate for RU/EN Compose-resource string parity (issue #74).
 *
 * Every `composeResources/values/strings.xml` (EN, the base locale) must have a
 * matching `values-ru/strings.xml` with the *exact same* set of `name` keys, and
 * vice-versa. When a key exists in only one locale, Compose falls back to the
 * base string, so a Russian user silently sees English (or a build breaks when
 * the RU-only key is referenced from code) — and that only surfaces at runtime,
 * so it's guarded here.
 *
 * Mirrors [StringResourcePlaceholderTest]: walks the repo from the Gradle module
 * dir up to the `settings.gradle.kts` root, then scans `composeResources` string
 * files. The `:utils:jvmTest` task declares every `strings.xml` as an input, so
 * editing one re-runs this gate instead of hitting UP-TO-DATE.
 */
class StringResourceParityTest : StringSpec({

    val keyPattern = Regex("""\bname="([^"]+)"""")

    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("settings.gradle.kts not found above ${System.getProperty("user.dir")}")
        }
        return dir
    }

    /**
     * Each `composeResources` directory that carries a localized `strings.xml`,
     * paired with its EN (base `values/`) and RU (`values-ru/`) files. Either
     * file may be absent on disk — the tests below treat a missing file as an
     * empty key set, which surfaces as a parity violation rather than a skip.
     */
    fun resourceBases(root: File): List<Triple<File, File, File>> = root
        .walkTopDown()
        .onEnter { it.name !in setOf("build", ".git", ".gradle", "node_modules") }
        .filter { it.isDirectory && it.name == "composeResources" }
        .filter { base ->
            File(File(base, "values"), "strings.xml").isFile ||
                File(File(base, "values-ru"), "strings.xml").isFile
        }
        .map { base ->
            Triple(
                base,
                File(File(base, "values"), "strings.xml"),
                File(File(base, "values-ru"), "strings.xml"),
            )
        }
        .toList()

    fun keysOf(file: File): Set<String> =
        if (file.isFile) {
            keyPattern.findAll(file.readText()).map { it.groupValues[1] }.toSet()
        } else {
            emptySet()
        }

    // Walk the tree once at spec construction; both tests reuse the result.
    val root = repoRoot()
    val bases = resourceBases(root)

    "localized composeResources string files are discovered" {
        bases.size shouldBeGreaterThan 0
    }

    "every EN string key has a RU counterpart and vice-versa" {
        val violations = bases.flatMap { (base, en, ru) ->
            val enKeys = keysOf(en)
            val ruKeys = keysOf(ru)
            val module = base.relativeTo(root)
            (enKeys - ruKeys).sorted().map { "$module: EN-only key missing from values-ru: $it" } +
                (ruKeys - enKeys).sorted().map { "$module: RU-only key missing from values: $it" }
        }
        violations.shouldBeEmpty()
    }
})
