plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kmpApp)
}

msApp {
    // Reuse androidApp's keystore — DEV_STORE_FILE in local.properties is relative
    // to that module; resolve through it explicitly.
    devKeystoreBaseDir = rootProject.layout.projectDirectory.dir("androidApp")
}

android {
    namespace = "com.georgeci.moneysurfer.offline"

    defaultConfig {
        applicationId = "com.georgeci.moneysurfer.offline"
        versionName = "${versionName}-offline"
    }
}

dependencies {
    implementation(projects.composeAppOffline)
}
