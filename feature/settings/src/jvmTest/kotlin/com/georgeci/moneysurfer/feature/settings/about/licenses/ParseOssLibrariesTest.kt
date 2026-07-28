package com.georgeci.moneysurfer.feature.settings.about.licenses

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Guards the schema mapping against the actual export committed to resources —
 * a format change in the AboutLibraries plugin output should fail here, not at
 * runtime on the Licenses screen.
 */
class ParseOssLibrariesTest : StringSpec({

    val exportFile = File("src/commonMain/composeResources/files/aboutlibraries.json")
    val libraries = parseOssLibraries(exportFile.readText())

    "parses the bundled aboutlibraries export" {
        libraries.shouldNotBeEmpty()
        libraries.shouldBeSortedBy { it.name.lowercase() }
        // Every bundled library must resolve at least one known license.
        libraries.count { it.licenses.isEmpty() } shouldBe 0
    }

    "collapses per-platform artifacts into a handful of family rows" {
        libraries.size shouldBeLessThan 30
        libraries.map { it.name } shouldContain "Jetpack Compose"

        val compose = libraries.first { it.name == "Jetpack Compose" }
        compose.artifacts shouldContain "androidx.compose.ui:ui-android 1.12.0-alpha03"
        compose.author shouldBe "The Android Open Source Project"
    }

    "drops BOM artifacts, which ship version constraints and no code" {
        libraries.flatMap { it.artifacts }.forEach { it shouldNotContain "-bom" }
    }

    "keeps every non-BOM artifact of the export attributed" {
        val exported = Regex("\"uniqueId\": ?\"([^\"]+)\"")
            .findAll(exportFile.readText())
            .map { it.groupValues[1] }
            .filterNot { it.substringAfter(':').endsWith("-bom") }
            .toSet()
        val listed = libraries.flatMap { library ->
            library.artifacts.map { it.substringBefore(' ') }
        }.toSet()

        listed shouldBe exported
    }

    "names a family the export does not map after the artifacts it bundles" {
        val parsed = parseOssLibraries(
            exportOf(
                artifact("com.example.tooling:core", version = "1.0", name = "Example Core"),
                artifact("com.example.tooling:io", version = "1.0", name = "Example IO"),
            ),
        )

        parsed.single().name shouldBe "Example Core, Example IO"
        parsed.single().id shouldBe "com.example.tooling"
    }

    "falls back to the group id when the export carries no names at all" {
        val parsed = parseOssLibraries(exportOf(artifact("com.example.unnamed:core", version = "1.0")))

        parsed.single().name shouldBe "com.example.unnamed"
        parsed.single().author shouldBe null
    }

    "hides the version when the artifacts of a family disagree on it" {
        val parsed = parseOssLibraries(
            exportOf(
                artifact("com.example.mixed:core", version = "1.0", name = "Mixed"),
                artifact("com.example.mixed:io", version = "2.0", name = "Mixed"),
            ),
        )

        parsed.single().version shouldBe null
        parsed.single().artifacts shouldBe listOf("com.example.mixed:core 1.0", "com.example.mixed:io 2.0")
    }

    "keeps the shared version when every artifact of a family agrees" {
        val parsed = parseOssLibraries(
            exportOf(
                artifact("com.example.same:core", version = "3.1", name = "Same"),
                artifact("com.example.same:io", version = "3.1", name = "Same"),
            ),
        )

        parsed.single().version shouldBe "3.1"
    }

    "prefers the longest matching group prefix" {
        val parsed = parseOssLibraries(
            exportOf(
                artifact("androidx.compose.ui:ui", version = "1.0", name = "Compose UI"),
                artifact("androidx.core:core", version = "1.0", name = "Core"),
            ),
        )

        parsed.map { it.name } shouldBe listOf("AndroidX (Android Jetpack)", "Jetpack Compose")
    }

    "credits the developers when the artifact declares no organization" {
        val parsed = parseOssLibraries(
            exportOf(
                artifact("com.example.dev:core", version = "1.0", name = "Dev", developer = "Ada Lovelace"),
            ),
        )

        parsed.single().author shouldBe "Ada Lovelace"
    }

    "ignores licenses the export does not describe" {
        val parsed = parseOssLibraries(
            exportOf(artifact("com.example.nolicense:core", version = "1.0", name = "No License", license = "GPL-3.0")),
        )

        parsed.single().licenses shouldBe emptyList()
    }

    "drops BOM artifacts even when they are the only ones of their group" {
        val parsed = parseOssLibraries(exportOf(artifact("com.example.platform:example-bom", version = "1.0")))

        parsed shouldBe emptyList()
    }
})

private fun artifact(
    uniqueId: String,
    version: String,
    name: String? = null,
    organization: String? = null,
    developer: String? = null,
    license: String = "Apache-2.0",
): String = buildString {
    append("""{"uniqueId":"$uniqueId","artifactVersion":"$version"""")
    name?.let { append(""","name":"$it"""") }
    organization?.let { append(""","organization":{"name":"$it"}""") }
    developer?.let { append(""","developers":[{"name":"$it"}]""") }
    append(""","licenses":["$license"]}""")
}

/** Minimal stand-in for the AboutLibraries export: only the fields the parser reads. */
private fun exportOf(vararg artifacts: String): String =
    """{"libraries":[${artifacts.joinToString()}],""" +
        """"licenses":{"Apache-2.0":{"name":"Apache License 2.0","content":"Apache text"}}}"""
