package com.georgeci.moneysurfer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class KmpLibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                testRuns["test"].executionTask.configure {
                    useJUnitPlatform()
                }
            }
            compilerOptions {
                freeCompilerArgs.add("-Xcontext-parameters")
            }

            extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
                androidResources {
                    enable = true
                }

                compileSdk = 36
                minSdk = 24

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                withHostTestBuilder {
                }

                withDeviceTestBuilder {
                    sourceSetTreeName = "test"
                }.configure {
                    instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            iosArm64 {

            }

            iosSimulatorArm64 {

            }
        }
    }
}
