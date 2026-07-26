plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
}

// Both `sync/api` and this module are called `api`, and KGP derives the klib
// `unique_name` from the project name — two libraries claiming `api_commonMain` make the
// KLIB loader pick one arbitrarily. Rename the artifact so the names stay distinct.
base {
    archivesName = "app-config-default"
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.appconfig"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kermit)
                implementation(projects.domain)
                implementation(projects.appConfig.api)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.turbine)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

koinCompiler {
    // Per-module verification can't see cross-module bindings (the layer sources are
    // implemented in :data-local and declared by the hosts). Full-graph verification
    // runs at :composeApp / :composeAppOffline — see `KoinModuleVerificationTest`.
    compileSafety = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
