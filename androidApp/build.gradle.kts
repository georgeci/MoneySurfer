plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kmpApp)
    alias(libs.plugins.easylauncher)
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

// Overlay a "DEV" ribbon on the launcher icon for debug installs so testers can
// tell dev builds from release on the home screen. Release builds keep the
// untouched icon.
easylauncher {
    buildTypes {
        register("debug") {
            filters(
                customRibbon(
                    label = "DEV",
                    ribbonColor = "#C0FF1744",
                    labelColor = "#FFFFFFFF",
                    position = "bottom",
                ),
            )
        }
    }
}

// Sonar Gradle plugin (≤6.0.x) still references the legacy AGP `AppExtension`,
// which AGP 9 removed. Skip this thin entry-point module — its sources are just
// the Activity host; real code analyzed via :composeApp / :feature / :domain.
sonar {
    isSkipProject = true
}
