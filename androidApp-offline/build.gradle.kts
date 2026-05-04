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

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    // No google-services / firebase-crashlytics — offline build never touches Firebase.
}

// Sonar Gradle plugin (≤6.0.x) still references the legacy AGP `AppExtension`,
// which AGP 9 removed. Skip this thin entry-point module.
sonar {
    isSkipProject = true
}

android {
    namespace = "com.georgeci.moneysurfer.offline.app"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.georgeci.moneysurfer.offline"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionProperties.requireInt("APP_VERSION_CODE")
        versionName = versionProperties.requireString("APP_VERSION_NAME") + "-offline"

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
        create("dev") {
            // Reuse androidApp's keystore — DEV_STORE_FILE in local.properties is
            // relative to that module; resolve through it explicitly.
            val storePath = secretOrEnv("DEV_STORE_FILE") ?: "keystore/dev.jks"
            storeFile = rootProject.file("androidApp/$storePath")
            storePassword = secretOrEnv("DEV_STORE_PASSWORD")
            keyAlias = secretOrEnv("DEV_KEY_ALIAS")
            keyPassword = secretOrEnv("DEV_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("dev")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeAppOffline)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.kotlin.stdlib)
    // No firebase-* deps.
}

fun secretOrEnv(name: String): String? {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
}

fun Properties.requireString(name: String): String =
    getProperty(name)?.trim()
        ?: error("Missing required version property: $name")

fun Properties.requireInt(name: String): Int =
    requireString(name).toIntOrNull()
        ?: error("Version property $name must be an integer")
