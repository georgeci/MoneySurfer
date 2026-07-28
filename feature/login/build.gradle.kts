plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kmp.lib)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.feature.login"
    }

    sourceSets.named("commonMain").configure {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.bundles.feature.compose)
                implementation(libs.bundles.feature.nav3)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.bundles.feature.koin)
                implementation(libs.bundles.feature.arrow)
                implementation(libs.kermit)
                implementation(projects.domain)
                implementation(projects.navigation)
                implementation(projects.sync.api)
                implementation(projects.uikit)
                implementation(projects.utils)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.bundles.feature.test)
                implementation(projects.domainTestFixtures)
                implementation(projects.syncTestFixtures)
            }
        }

        androidMain {
            dependencies {
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.testExt.junit)
            }
        }

        getByName("androidHostTest") {
            // Shared Roborazzi capture harness — see gradle/screenshot-tests.gradle.kts.
            kotlin.srcDir(rootProject.file("gradle/screenshot-harness/kotlin"))
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(projects.uikit)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        iosMain {
            dependencies {
                // No iOS-specific deps.
            }
        }
    }
}

// Pin the generated `Res` class package so callers can import `moneysurfer.feature.login.generated.resources.*`
// regardless of where compose-multiplatform's default would land it for nested gradle paths.
compose.resources {
    packageOfResClass = "moneysurfer.feature.login.generated.resources"
}

koinCompiler {
    // Per-module verification fails because login depends on data/domain/sync/shared bindings
    // that aren't visible from this module. Full-graph verification runs at the
    // :composeApp module instead (AppModule includes every sub-module).
    compileSafety = false
}

dependencies {
    add("kspCommonMainMetadata", libs.arrow.optics.ksp)
}

tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.uiToolingPreview)
}

// Full-screen Roborazzi captures of onboarding and sign-in.
// See docs/testing/screenshot-tests.md.
apply(from = rootProject.file("gradle/screenshot-tests.gradle.kts"))
