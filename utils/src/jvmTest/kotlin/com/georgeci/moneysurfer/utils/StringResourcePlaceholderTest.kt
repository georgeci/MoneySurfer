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

    // Matches `%s` / `%d` (any case) — indexed forms like `%1$s` never match
    // because the conversion letter isn't directly after `%`.
    val barePlaceholder = Regex("""%[sdSD]""")

    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("settings.gradle.kts not found above ${System.getProperty("user.dir")}")
        }
        return dir
    }

    fun stringResourceFiles(): List<File> = repoRoot()
        .walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
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
