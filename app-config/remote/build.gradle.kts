plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kmp.lib)
}

// KGP derives the klib `unique_name` from the project name, so the two sibling modules already
// rename their artifacts. Keep this one in the same scheme rather than leaving a bare `remote`
// around for a future `sync/remote` to collide with.
base {
    archivesName = "app-config-remote"
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.appconfig.remote"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kermit)
                implementation(libs.gitlive.firebase.common)
                implementation(libs.gitlive.firebase.firestore)
                implementation(projects.appConfig.api)
                // `ConfigRegistry` — the only place that can answer "is this name a known key, and
                // is it `remoteOverridable`?", because keys are `internal` to the modules that own
                // their facades. Its KDoc names the remote-config pull as an intended consumer.
                implementation(projects.appConfig.default)
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
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        androidMain {
            dependencies {
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.firestore)
            }
        }
    }
}

koinCompiler {
    // Per-module verification cannot see cross-module bindings — `RemoteConfigMirror` is
    // implemented in :data-local and bound by the online host. Full-graph verification runs at
    // :composeApp — see `KoinModuleVerificationTest`.
    compileSafety = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
