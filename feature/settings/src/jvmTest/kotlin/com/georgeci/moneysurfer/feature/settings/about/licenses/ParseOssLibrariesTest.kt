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
})
