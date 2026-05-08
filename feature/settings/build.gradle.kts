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
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.feature.settings"
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
                implementation(libs.compose.material3.adaptive.navigation3)
                implementation(libs.bundles.feature.nav3)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.bundles.feature.koin)
                implementation(libs.bundles.feature.arrow)
                implementation(libs.kermit)
                implementation(projects.domain)
                implementation(projects.dataLocal)
                implementation(projects.navigation)
                implementation(projects.sync.api)
                implementation(projects.uikit)
                implementation(projects.utils)
                implementation(libs.okio)
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
                implementation(libs.androidx.activity.compose)
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

// Pin the generated `Res` class package so callers can import `moneysurfer.feature.settings.generated.resources.*`
// regardless of where compose-multiplatform's default would land it for nested gradle paths.
compose.resources {
    packageOfResClass = "moneysurfer.feature.settings.generated.resources"
}

koinCompiler {
    // Per-module verification fails because settings depends on data/domain/sync/shared bindings
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
