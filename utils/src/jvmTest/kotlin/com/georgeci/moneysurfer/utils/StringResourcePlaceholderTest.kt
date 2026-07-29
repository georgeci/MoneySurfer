package com.georgeci.moneysurfer.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Repo-wide gate for the AGENTS.md rule "All Compose resource string
 * placeholders must be indexed: `%1$s`, `%1$d`. Never use bare `%s` or `%d`."
 * Bare placeholders break Compose Multiplatform string formatting on some
 * targets, and the breakage only shows up at runtime — so it's guarded here.
 */
class StringResourcePlaceholderTest : StringSpec({

    // Matches any format specifier without a `1$`-style index: bare `%s`/`%d`
    // as well as width/precision forms like `%02d` or `%.3s`. Indexed forms
    // (`%1$s`, `%2$10d`) never match because `$` can't appear before the
    // conversion letter here. Literal percent signs ("80%.") don't match
    // because they aren't followed by a conversion letter.
    val barePlaceholder = Regex("""%[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

    fun stringResourceFiles(): List<File> = repoRoot()
        .walkTopDown()
        .onEnter { it.name !in skippedDirs }
        .filter { it.isFile && it.name == "strings.xml" }
        .toList()

    "strings.xml files are discovered" {
        stringResourceFiles().size shouldBeGreaterThan 0
    }

    "string resources use only indexed placeholders" {
        val violations = stringResourceFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                barePlaceholder.find(line)?.let {
                    "${file.relativeTo(repoRoot())}:${index + 1}: ${line.trim()}"
                }
            }
        }
        violations.shouldBeEmpty()
    }
})
