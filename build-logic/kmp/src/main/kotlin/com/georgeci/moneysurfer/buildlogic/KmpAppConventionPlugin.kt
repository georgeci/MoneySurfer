package com.georgeci.moneysurfer.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.project.starter.easylauncher.filter.ColorRibbonFilter
import com.project.starter.easylauncher.plugin.EasyLauncherExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import java.util.Properties

@Suppress("unused")
class KmpAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val localProps = loadLocalProperties()
        val versionProps = loadVersionProperties()
        val versionCodeInt = versionProps.requireInt("APP_VERSION_CODE")
        val versionNameStr = versionProps.requireString("APP_VERSION_NAME")

        val secrets = Secrets(localProps, providers)

        val hasReleaseSigning = listOf(
            "RELEASE_STORE_FILE",
            "RELEASE_STORE_PASSWORD",
            "RELEASE_KEY_ALIAS",
            "RELEASE_KEY_PASSWORD",
        ).all { secrets[it] != null }

        // Debug is normally signed with the `dev` keystore so the `.dev` flavor
        // keeps a stable identity across local installs. CI (and fresh forks)
        // have neither keystore/dev.jks nor DEV_* secrets, so fall back to AGP's
        // auto-generated debug keystore there — the same graceful degradation the
        // `release` type gets from `hasReleaseSigning`. Without this, every
        // debug packaging task fails with "storeFile … dev.jks … doesn't exist".
        val devStorePath = secrets["DEV_STORE_FILE"] ?: "keystore/dev.jks"
        val devStoreFile = rootProject.file(devStorePath)
        val hasDevSigning = devStoreFile.exists()

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val compileSdkInt = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        val minSdkInt = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        val targetSdkInt = libs.findVersion("android-targetSdk").get().requiredVersion.toInt()

        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                compileSdk {
                    version = release(compileSdkInt)
                }

                defaultConfig {
                    minSdk = minSdkInt
                    targetSdk = targetSdkInt
                    versionCode = versionCodeInt
                    versionName = versionNameStr
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildFeatures {
                    buildConfig = true
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                signingConfigs {
                    // Keystore paths in local.properties / env are always resolved
                    // relative to the repo root, regardless of which app module
                    // applies this convention (see `devStoreFile` above). Skip the
                    // `dev` config entirely when the keystore is missing so debug
                    // falls back to AGP's default debug signing.
                    if (hasDevSigning) {
                        create("dev") {
                            storeFile = devStoreFile
                            storePassword = secrets["DEV_STORE_PASSWORD"]
                            keyAlias = secrets["DEV_KEY_ALIAS"]
                            keyPassword = secrets["DEV_KEY_PASSWORD"]
                        }
                    }
                    if (hasReleaseSigning) {
                        create("release") {
                            storeFile = rootProject.file(secrets["RELEASE_STORE_FILE"]!!)
                            storePassword = secrets["RELEASE_STORE_PASSWORD"]
                            keyAlias = secrets["RELEASE_KEY_ALIAS"]
                            keyPassword = secrets["RELEASE_KEY_PASSWORD"]
                        }
                    }
                }

                buildTypes {
                    getByName("debug") {
                        applicationIdSuffix = ".dev"
                        // When the dev keystore is absent (CI/forks) leave the
                        // default debug signingConfig in place instead.
                        if (hasDevSigning) {
                            signingConfig = signingConfigs.getByName("dev")
                        }
                    }
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            rootProject.file("proguard/proguard-rules.pro"),
                        )
                        ndk {
                            debugSymbolLevel = "FULL"
                        }
                        if (hasReleaseSigning) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                bundle {
                    abi {
                        @Suppress("UnstableApiUsage")
                        enableSplit = true
                    }
                    density {
                        @Suppress("UnstableApiUsage")
                        enableSplit = true
                    }
                    language {
                        // false = все языки в base install (без dynamic language split)
                        @Suppress("UnstableApiUsage")
                        enableSplit = false
                    }
                }
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-core-splashscreen").get())
                add("implementation", libs.findLibrary("material").get())
                add("implementation", libs.findLibrary("androidx-activity-compose").get())
                add("implementation", libs.findLibrary("compose-uiToolingPreview").get())
                add("debugImplementation", libs.findLibrary("compose-uiTooling").get())
                add("implementation", platform(libs.findLibrary("koin-bom").get()))
                add("implementation", libs.findLibrary("koin-android").get())
                add("implementation", libs.findLibrary("kotlin-stdlib").get())
            }

            // Overlay a ribbon on the launcher icon for debug installs so testers
            // can tell dev builds from release on the home screen. Offline app
            // module gets "OFF DEV" so it's also distinguishable from the online
            // dev build sitting next to it. Release icons stay untouched.
            val ribbonLabel = if (project.name.contains("offline", ignoreCase = true)) {
                "OFF DEV"
            } else {
                "DEV"
            }
            pluginManager.apply("com.starter.easylauncher")
            extensions.configure<EasyLauncherExtension> {
                buildTypes.register("debug") {
                    filters(
                        customRibbon(
                            label = ribbonLabel,
                            ribbonColor = "#C0FF1744",
                            labelColor = "#FFFFFFFF",
                            gravity = ColorRibbonFilter.Gravity.BOTTOM,
                        ),
                    )
                }
            }
        }

        // Skip Sonar — these app modules are thin entry-points; analysis lives in
        // :composeApp / :feature / :domain. Sonar Gradle plugin (≤6.0.x) still
        // references the legacy AGP `AppExtension` removed in AGP 9, so analyzing
        // them would crash configuration anyway.
        plugins.withId("org.sonarqube") {
            extensions.findByName("sonar")?.let { sonarExt ->
                sonarExt.javaClass.methods
                    .firstOrNull { it.name == "setSkipProject" && it.parameterCount == 1 }
                    ?.invoke(sonarExt, true)
            }
        }
    }
}

private class Secrets(
    private val local: Properties,
    private val providers: ProviderFactory,
) {
    operator fun get(name: String): String? =
        local.getProperty(name) ?: providers.environmentVariable(name).orNull
}

private fun Project.loadLocalProperties(): Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

private fun Project.loadVersionProperties(): Properties = Properties().apply {
    rootProject.file("Version.xcconfig").inputStream().use { load(it) }
}

private fun Properties.requireString(name: String): String =
    getProperty(name)?.trim()
        ?: error("Missing required version property: $name")

private fun Properties.requireInt(name: String): Int =
    requireString(name).toIntOrNull()
        ?: error("Version property $name must be an integer")
