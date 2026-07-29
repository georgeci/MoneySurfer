package com.georgeci.moneysurfer.utils

import java.io.File

/**
 * Directories a repo-wide walk must not descend into.
 *
 * Shared by the repo-wide textual gates ([StringResourcePlaceholderTest], [StringResourceParityTest],
 * [StringResourceEscapeTest], [MaestroFlowSelectorTest]). All four walk the checkout from the Gradle
 * module dir up to the `settings.gradle.kts` root and scan by file name, so they need the exact same
 * idea of "which directories aren't source".
 *
 * `.claude` is on the list because Claude Code keeps its worktrees under `.claude/worktrees/` —
 * *inside* the main checkout. Without it, a gate run from the main clone scans every other
 * worktree's branch too, so an unfinished string in a sibling branch fails the main checkout's
 * tests, and the failure names a path that isn't in the current diff. Keep this in sync with the
 * `exclude(...)` lists that declare these files as `:utils:jvmTest` inputs in
 * `utils/build.gradle.kts` — a directory skipped here but hashed there only costs up-to-date
 * checks; the reverse makes the gate flaky.
 */
val skippedDirs = setOf("build", ".git", ".gradle", ".claude", "node_modules")

/** The checkout root: the nearest ancestor of the working dir holding `settings.gradle.kts`. */
fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir")).absoluteFile
    while (!File(dir, "settings.gradle.kts").exists()) {
        dir = dir.parentFile ?: error("settings.gradle.kts not found above ${System.getProperty("user.dir")}")
    }
    return dir
}
