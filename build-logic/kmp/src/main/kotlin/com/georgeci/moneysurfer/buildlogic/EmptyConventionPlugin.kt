package com.georgeci.moneysurfer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class EmptyConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.logger.info("Applying ms.empty-convention to ${target.path}")
    }
}

