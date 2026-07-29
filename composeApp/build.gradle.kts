import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

val desktopPackageVersion = Properties().apply {
    rootProject.file("Version.xcconfig").inputStream().use(::load)
}.let { version ->
    val major = version.getProperty("APP_VERSION_MAJOR")?.trim()
        ?: error("Missing APP_VERSION_MAJOR in Version.xcconfig")
    val minor = version.getProperty("APP_VERSION_MINOR")?.trim()
        ?: error("Missing APP_VERSION_MINOR in Version.xcconfig")
    val build = providers.environmentVariable("APP_BUILD_NUMBER").orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: version.getProperty("APP_BUILD_NUMBER")?.trim()
        ?: error("Missing APP_BUILD_NUMBER in Version.xcconfig")
    listOf(major, minor, build).forEach { component ->
        require(component.toIntOrNull() != null) {
            "Desktop package version component must be an integer: $component"
        }
    }
    "$major.$minor.$build"
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.kmp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Required when using NativeSQLiteDriver
            linkerOpts.add("-lsqlite3")
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.firestore)
        }
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(projects.domain)
            implementation(projects.appConfig.api)
            implementation(projects.appConfig.default)
            implementation(projects.appConfig.remote)
            implementation(projects.feature.login)
            implementation(projects.feature.transaction)
            // The RemoteGlobal layer's DataStore mirror is created by a per-platform factory here,
            // not in `sharedPlatformModule`: only the online build has a remote layer to mirror.
            implementation(projects.dataLocal)
            implementation(projects.dataRemote)
            implementation(projects.syncSurfer)
            implementation(projects.sync.default)

            implementation(libs.compose.runtime)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(projects.dataLocal)
            implementation(projects.feature.login)
            // DashboardCustomizeScreenStateTest mounts the customize screen's stateless content.
            implementation(projects.feature.dashboard)
            // PercentFormattingTest mounts the budget details content to assert what Compose
            // actually renders for a `%`-carrying format string.
            implementation(projects.feature.budget)
            // DebugConfigScreenStateTest mounts the QA configuration panel's stateless content.
            implementation(projects.feature.settings)
            // SyncScreenStateTest feeds the sync panel real outbox rows and cursors.
            implementation(projects.sync.api)
            // GlobalErrorBoundaryTest drives the real boundary component and the real
            // MviViewModel escalation path (issue #78).
            implementation(projects.uikit)
            implementation(projects.utils)
            implementation(libs.compose.material3)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.compose.uiTest)
            // KoinModuleVerificationTest lists the `parametersOf` types, some of
            // which are navigation-level (goal contribution mode) or owned by a
            // feature (the category picker's layout variant).
            implementation(projects.navigation)
            implementation(projects.feature.category)
            implementation(libs.kotest.runner.junit5)
            implementation(libs.fixture.monkey.kotlin)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

koinCompiler {
    // Compile-time graph validation in koin-compiler 1.0.0-RC1 only sees the current
    // Gradle module's classes — cross-module @KoinViewModel lookups (e.g. AppViewModel
    // declared in :shared but resolved from :composeApp) flag as "missing definition"
    // even though they exist. Verification runs at test-time instead — see
    // `KoinModuleVerificationTest` in jvmTest.
    compileSafety = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    // Compose desktop UI tests (`runComposeUiTest`) render offscreen through Skiko, so they must
    // not need a display. Forcing headless mode here keeps that true on developer machines too,
    // instead of only being exercised on CI's display-less `ubuntu-latest`.
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "com.georgeci.moneysurfer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.georgeci.moneysurfer"
            packageVersion = desktopPackageVersion
        }
    }
}
