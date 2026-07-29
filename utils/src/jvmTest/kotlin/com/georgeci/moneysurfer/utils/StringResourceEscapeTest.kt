package com.georgeci.moneysurfer.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Repo-wide gate against Android's `\'` / `\"` escapes in Compose resources.
 *
 * `aapt` unescapes those before the string reaches the app; Compose Multiplatform's resource
 * pipeline does not — the backslash survives into the generated `.cvr` payload and is rendered
 * verbatim, so the user reads `You don\'t have access`. XML needs no escaping for either character
 * inside element text, so the plain form is both correct and portable.
 *
 * Nine strings shipped with the escape before this gate existed (see PR #374); the breakage is
 * invisible in review and in every unit test that doesn't render the string, which is why it is
 * guarded here.
 *
 * Mirrors [StringResourcePlaceholderTest]: walks from the Gradle module dir up to the
 * `settings.gradle.kts` root, then scans every `strings.xml` / `plurals.xml`. The `:utils:jvmTest`
 * task declares those files as inputs, so editing one re-runs this gate instead of hitting
 * UP-TO-DATE.
 */
class StringResourceEscapeTest : StringSpec({

    // A backslash before a quote character. `\n` and friends stay legal — only the quote escapes
    // are the ones aapt would have consumed and compose-resources keeps.
    val quoteEscape = Regex("""\\['"]""")

    fun resourceFiles(): List<File> = repoRoot()
        .walkTopDown()
        .onEnter { it.name !in skippedDirs }
        .filter { it.isFile && it.name in setOf("strings.xml", "plurals.xml") }
        .toList()

    "resource files are discovered" {
        resourceFiles().size shouldBeGreaterThan 0
    }

    "no string resource escapes an apostrophe or a quote the Android way" {
        val violations = resourceFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                quoteEscape.find(line)?.let {
                    "${file.relativeTo(repoRoot())}:${index + 1}: ${line.trim()} " +
                        "— use a plain ' or \" (compose-resources does not unescape these)"
                }
            }
        }
        violations.shouldBeEmpty()
    }
})
