package com.georgeci.moneysurfer.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Repo-wide gate binding the Maestro flows to the Compose `testTag`s they drive (issue #352).
 *
 * Every `id:` selector in `scripts/maestro/` must correspond to a tag literal declared in Kotlin.
 * Nothing else enforces that pairing: the flows are YAML, the tags are constants, and a renamed
 * or deleted tag leaves the flow pointing at a node that no longer exists. That failure only
 * shows up on a device, in a nightly suite that is currently disabled (#297) — which is exactly
 * how flow `09` came to tap an `Income` chip the filters screen had replaced, and how `10` ended
 * up typing an IBAN into the opening-balance field.
 *
 * Deliberately textual rather than reference-based: the tags live in a dozen feature modules that
 * `:utils` neither depends on nor should. Mirrors [StringResourceParityTest] — walk up to the
 * `settings.gradle.kts` root, then scan. The `:utils:jvmTest` task declares the flows and the
 * screen sources as inputs, so editing either re-runs this gate instead of hitting UP-TO-DATE.
 *
 * What it does *not* check: that the tag is applied to a node which is actually composed in the
 * state the flow expects. `dashboard:addAccount` resolves here yet only exists while the account
 * list is empty — see `scripts/maestro/README.md`.
 */
class MaestroFlowSelectorTest : StringSpec({

    /** `"screen:element"` — a literal tag value. */
    val tagLiteral = Regex(""""([a-zA-Z]+:[A-Za-z0-9:_\-]*)"""")

    /** `"screen:element:"` used as a prefix, or `"screen:element:${'$'}{enum.name.lowercase()}"`. */
    val tagPrefixInterpolated = Regex(""""([a-zA-Z]+:[A-Za-z0-9:_\-]*)\$\{""")

    /** `id: "screen:element"` in a flow, ignoring commented-out lines. */
    val flowSelector = Regex("""\bid:\s*"([^"]+)"""")

    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("settings.gradle.kts not found above ${System.getProperty("user.dir")}")
        }
        return dir
    }

    fun kotlinSources(root: File): Sequence<File> = root
        .walkTopDown()
        .onEnter { it.name !in setOf("build", ".git", ".gradle", "node_modules") }
        .filter { it.isFile && it.extension == "kt" }

    fun flowFiles(root: File): List<File> = File(root, "scripts/maestro")
        .walkTopDown()
        .filter { it.isFile && it.extension == "yaml" }
        .toList()

    "every id: selector in a Maestro flow resolves to a testTag declared in Kotlin" {
        val root = repoRoot()

        val literals = mutableSetOf<String>()
        val prefixes = mutableSetOf<String>()
        kotlinSources(root).forEach { file ->
            val text = file.readText()
            tagLiteral.findAll(text).forEach { match ->
                val value = match.groupValues[1]
                if (value.endsWith(":")) prefixes += value else literals += value
            }
            tagPrefixInterpolated.findAll(text).forEach { prefixes += it.groupValues[1] }
        }

        val flows = flowFiles(root)
        flows.size shouldBeGreaterThan 0

        val unresolved = flows.flatMap { file ->
            file.readLines()
                .withIndex()
                .filterNot { (_, line) -> line.trimStart().startsWith("#") }
                .mapNotNull { (index, line) ->
                    flowSelector.find(line)?.groupValues?.get(1)?.let { tag -> Triple(file, index + 1, tag) }
                }
        }.filterNot { (_, _, tag) ->
            tag in literals || prefixes.any { tag.startsWith(it) }
        }.map { (file, line, tag) ->
            "${file.relativeTo(root)}:$line declares id \"$tag\", which no Kotlin testTag provides"
        }

        unresolved.shouldBeEmpty()
    }

    "the flows actually carry id: selectors, so the gate above cannot pass vacuously" {
        val root = repoRoot()
        val selectorCount = flowFiles(root).sumOf { file ->
            file.readLines().count { line ->
                !line.trimStart().startsWith("#") && flowSelector.containsMatchIn(line)
            }
        }
        selectorCount shouldBeGreaterThan 50
    }
})
