package com.georgeci.moneysurfer.feature.settings.about.licenses

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Guards the schema mapping against the actual export committed to resources —
 * a format change in the AboutLibraries plugin output should fail here, not at
 * runtime on the Licenses screen.
 */
class ParseOssLibrariesTest : StringSpec({

    val exportFile = File("src/commonMain/composeResources/files/aboutlibraries.json")

    "parses the bundled aboutlibraries export" {
        val libraries = parseOssLibraries(exportFile.readText())

        libraries.shouldNotBeEmpty()
        libraries.shouldBeSortedBy { it.name.lowercase() }
        // Every bundled library must resolve at least one known license.
        libraries.count { it.licenses.isEmpty() } shouldBe 0
    }
})
