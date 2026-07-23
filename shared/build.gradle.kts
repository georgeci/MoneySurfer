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
    alias(libs.plugins.aboutlibraries)
}

// OSS-licenses metadata for the Settings → About → Open-source licenses screen.
// `shared` sees the full common dependency graph, so the export runs here; the
// JSON lands in feature:settings resources where the Licenses screen reads it.
// Regenerate with: ./gradlew :shared:exportLibraryDefinitions
aboutLibraries {
    export {
        outputFile = file(
            "../feature/settings/src/commonMain/composeResources/files/aboutlibraries.json",
        )
        excludeFields.addAll("funding", "scm")
    }
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.shared"
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets.named("commonMain").configure {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.navigation3.resultstate)
                implementation(libs.kotlinx.serialization.core)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.koin.annotations)
                implementation(projects.domain)
                implementation(projects.uikit)
                implementation(projects.utils)
                implementation(projects.dataLocal)
                implementation(projects.navigation)
                implementation(projects.feature.account)
                implementation(projects.feature.budget)
                implementation(projects.feature.category)
                implementation(projects.feature.dashboard)
                implementation(projects.feature.goal)
                implementation(projects.feature.login)
                implementation(projects.feature.settings)
                implementation(projects.feature.transaction)
                implementation(projects.feature.workspace)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.koin.compose)
                implementation(libs.arrow.core)
                implementation(libs.arrow.optics)
                implementation(libs.kermit)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(projects.domainTestFixtures)
                implementation(projects.syncTestFixtures)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
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
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }
    }
}

koinCompiler {
    // Per-module verification fails because shared depends on data/domain/sync bindings
    // that aren't visible from this module. Full-graph verification runs at the
    // :composeApp module instead (AppModule includes every sub-module).
    compileSafety = false
}

dependencies {
    // Arrow optics generates code via `kspCommonMainMetadata` only; the output is wired into
    // commonMain (see `sourceSets.named("commonMain")` above) so every target picks it up via
    // the multiplatform source-set hierarchy. Adding the processor to per-target KSP configs
    // would duplicate the generated declarations.
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