package com.georgeci.moneysurfer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Marker plugin — never applied to a project.
 *
 * A Gradle plugin id needs an implementation class, and the id is what
 * `settings.gradle.kts` declares (`id("ms.qa-tools") apply false`) to pull this
 * module onto the settings classloader scope. That scope is an ancestor of
 * every build script *and* of the script plugins applied with
 * `apply(from = ...)`, which is how [MaestroTools] / [AllureTools] become
 * visible to `gradle/qa.gradle.kts`.
 *
 * Declaring it in the root build script's own `plugins { }` block instead does
 * not work: that classpath is not visible to a script plugin the same script
 * applies, and it would add this jar to the dependency-locked root buildscript
 * classpath.
 */
class QaToolsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.logger.info("ms.qa-tools is a classpath marker; applying it to ${target.path} does nothing")
    }
}
