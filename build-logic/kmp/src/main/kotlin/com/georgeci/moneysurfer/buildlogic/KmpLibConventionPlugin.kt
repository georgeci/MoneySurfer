package com.georgeci.moneysurfer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class KmpLibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val libs = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        val compileSdkInt = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        val minSdkInt = libs.findVersion("android-minSdk").get().requiredVersion.toInt()

        target.extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                testRuns["test"].executionTask.configure {
                    useJUnitPlatform()
                }
            }
            extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
                androidResources {
                    enable = true
                }

                compileSdk = compileSdkInt
                minSdk = minSdkInt

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                withHostTestBuilder {
                }.configure {
                    // Robolectric (and therefore Roborazzi screenshot tests) needs the merged
                    // Android resources/assets on the host-test classpath — without this the
                    // compose-multiplatform `Res` bundle is missing and every string renders
                    // blank. Harmless for modules with no screenshot tests.
                    isIncludeAndroidResources = true
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
