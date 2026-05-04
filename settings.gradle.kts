rootProject.name = "MoneySurfer"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":composeApp")
include(":domain")
include(":sync:api")
include(":sync:default")
//include(":sync:no-op")
include(":feature")
include(":uikit")
include(":shared")
include(":sync-test-fixtures")
include(":domain-test-fixtures")
include(":data-test-fixtures")
include(":integration-test")
include(":feature:dashboard")
include(":feature:settings")
include(":feature:transaction")
include(":feature:login")
include(":feature:account")
include(":feature:category")
include(":feature:workspace")
include(":utils")
include(":navigation")
include(":data-local")
include(":data-remote")
include(":sync-surfer")
include(":navigation")
//include(":androidApp-offline")
