plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.offline.lib"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeAppOffline"
            isStatic = true
            // Required when using NativeSQLiteDriver
            linkerOpts.add("-lsqlite3")
        }
    }

    // jvm target used purely to run the Koin-graph verification on the offline
    // wiring; no Compose desktop entry point is published from this module.
    jvm()

    sourceSets {
        commonMain.dependencies {
            // Offline host wires the local-only graph; data-remote / sync-surfer /
            // sync.default are intentionally absent so Firebase / gitlive-firebase
            // can never end up on the offline classpath.
            implementation(projects.shared)
            implementation(projects.domain)
            implementation(projects.appConfig.api)
            implementation(projects.appConfig.default)
            implementation(projects.feature.login)
            implementation(projects.feature.transaction)
            implementation(projects.sync.api)
            implementation(projects.sync.noOp)
            implementation(libs.compose.runtime)
            implementation(libs.arrow.core)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.domainTestFixtures)
        }
        jvmTest.dependencies {
            implementation(projects.dataLocal)
            // OfflineKoinModuleVerificationTest lists the `parametersOf` types, some
            // of which are navigation-level (goal contribution mode) or owned by a
            // feature (the category picker's layout variant).
            implementation(projects.navigation)
            implementation(projects.feature.category)
            implementation(libs.kotest.runner.junit5)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.room.runtime)
        }
        iosMain.dependencies {
        }
    }
}

koinCompiler {
    compileSafety = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
