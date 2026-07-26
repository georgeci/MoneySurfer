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
    // Supply-chain hardening (issue #158): forbid subprojects from declaring
    // their own repositories. Every dependency must resolve through the
    // curated, group-scoped repositories below — a rogue `repositories { }`
    // block in any module (or injected by a compromised plugin) fails the
    // build instead of silently widening the trusted source set.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

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
include(":androidApp-offline")
include(":composeApp")
include(":composeAppOffline")
include(":domain")
// `app-config`, not `config` — `config/detekt` already owns that directory.
include(":app-config:api")
include(":app-config:default")
include(":app-config:remote")
include(":sync:api")
include(":sync:default")
include(":sync:no-op")
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
include(":feature:budget")
include(":feature:workspace")
include(":feature:goal")
include(":utils")
include(":navigation")
include(":detekt-rules")
include(":data-local")
include(":data-remote")
include(":sync-surfer")
