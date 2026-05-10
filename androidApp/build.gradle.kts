import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kmpApp)
    alias(libs.plugins.playPublisher)
}

android {
    namespace = "com.georgeci.moneysurfer"

    defaultConfig {
        applicationId = "com.georgeci.moneysurfer"

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

    buildTypes {
        getByName("release") {
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
            }
        }
    }
}

dependencies {
    implementation(projects.composeApp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}

// Google Play upload via gradle-play-publisher. Service-account JSON path comes
// from `PLAY_SERVICE_ACCOUNT_JSON` (env or local.properties); defaults to
// `play-service-account.json` at the repo root. If the file is absent the
// plugin's tasks are disabled so local builds without creds don't fail.
run {
    val credsPath = providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON").orNull
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
            Properties().apply { f.inputStream().use { load(it) } }
                .getProperty("PLAY_SERVICE_ACCOUNT_JSON")
        }
        ?: "play-service-account.json"
    val credsFile = rootProject.file(credsPath)
    play {
        enabled.set(credsFile.exists())
        if (credsFile.exists()) {
            serviceAccountCredentials.set(credsFile)
        }
        defaultToAppBundles.set(true)
        track.set("internal")
    }
}

// Sonar Gradle plugin (≤6.0.x) still references the legacy AGP `AppExtension`,
// which AGP 9 removed. Skip this thin entry-point module — its sources are just
// the Activity host; real code analyzed via :composeApp / :feature / :domain.
sonar {
    isSkipProject = true
}
