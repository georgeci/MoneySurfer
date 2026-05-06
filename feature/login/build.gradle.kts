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
                implementation(projects.navigation)
                implementation(projects.sync.api)
                implementation(projects.uikit)
                implementation(projects.utils)
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
                // No iOS-specific deps.
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
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
