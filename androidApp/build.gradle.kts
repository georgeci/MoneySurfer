import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val versionProperties = Properties().apply {
    rootProject.file("Version.xcconfig").inputStream().use { load(it) }
}

val hasReleaseSigning = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
).all { secretOrEnv(it) != null }

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.georgeci.moneysurfer"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.georgeci.moneysurfer"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionProperties.requireInt("APP_VERSION_CODE")
        versionName = versionProperties.requireString("APP_VERSION_NAME")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Emulator toggle baked into BuildConfig. Flip via Gradle property:
        //   ./gradlew :androidApp:assembleDebug -PuseEmulator=true
        // Production release builds never see `useEmulator=true` from CI / Play Store
        // runners, so the field stays `false`.
        buildConfigField(
            "boolean",
            "USE_EMULATOR",
            (project.findProperty("useEmulator") == "true").toString(),
        )
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
        create("dev") {
            storeFile = file(secretOrEnv("DEV_STORE_FILE") ?: "keystore/dev.jks")
            storePassword = secretOrEnv("DEV_STORE_PASSWORD")
            keyAlias = secretOrEnv("DEV_KEY_ALIAS")
            keyPassword = secretOrEnv("DEV_KEY_PASSWORD")
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(secretOrEnv("RELEASE_STORE_FILE")!!)
                storePassword = secretOrEnv("RELEASE_STORE_PASSWORD")
                keyAlias = secretOrEnv("RELEASE_KEY_ALIAS")
                keyPassword = secretOrEnv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("dev")
        }
        getByName("release") {
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "FULL"
            }
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
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
    implementation(projects.composeApp)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.kotlin.stdlib)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}


fun secretOrEnv(name: String): String? {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
}

fun requireSecret(name: String): String {
    return secretOrEnv(name)
        ?: error("Missing required secret: $name")
}

fun Properties.requireString(name: String): String =
    getProperty(name)?.trim()
        ?: error("Missing required version property: $name")

fun Properties.requireInt(name: String): Int =
    requireString(name).toIntOrNull()
        ?: error("Version property $name must be an integer")
