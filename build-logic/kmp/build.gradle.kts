plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kmp.plugin)
    compileOnly(libs.kmp.plugin.lib)
    // Implementation (not compileOnly) so easylauncher ends up on the
    // convention plugin's runtime classpath — needed because the plugin is
    // applied via pluginManager.apply("com.starter.easylauncher") rather than
    // the consuming module declaring `alias(libs.plugins.easylauncher)`.
    implementation(libs.easylauncher.plugin)
    // gradle-play-publisher is applied dynamically by the convention plugin via
    // pluginManager.apply, so the plugin classes must be on the runtime
    // classpath of build-logic itself (not just compileOnly).
    implementation(libs.play.publisher.plugin)
    // ReleaseStatus / TrackType live in the android-publisher artifact, not in
    // the gradle plugin itself — needs to be declared explicitly so Kotlin can
    // resolve the enum at compile time.
    implementation(libs.play.publisher.android)
//    compileOnly(libs.plugins.androidLint)
//    compileOnly(libs.plugins.composeMultiplatform)
//    compileOnly(libs.plugins.composeCompiler)
//    compileOnly(libs.plugins.kotlinSerialization)
//    compileOnly(libs.plugins.ksp)
//    compileOnly(libs.plugins.koin.compiler)
//    compileOnly(libs.plugins.kotest.multiplatform)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
//
//repositories {
//    google()
//    mavenCentral()
//    gradlePluginPortal()
//}
//
gradlePlugin {
    plugins {
        register("emptyConvention") {
            id = "ms.empty-convention"
            implementationClass = "com.georgeci.moneysurfer.buildlogic.EmptyConventionPlugin"
            displayName = "MoneySurfer Empty Convention Plugin"
            description = "No-op convention plugin scaffold for future shared build logic."
        }
        register("kmpLibConvention") {
            id = "kmp.lib"
            implementationClass = "com.georgeci.moneysurfer.buildlogic.KmpLibConventionPlugin"
            displayName = "MoneySurfer KMP Lib Convention Plugin"
            description = "Temporary no-op plugin for KMP library modules."
        }
        register("kmpAppConvention") {
            id = "kmp.app"
            implementationClass = "com.georgeci.moneysurfer.buildlogic.KmpAppConventionPlugin"
            displayName = "MoneySurfer KMP App Convention Plugin"
            description = "Temporary no-op plugin for KMP app modules."
        }
        register("playPublisherConvention") {
            id = "ms.play-publisher"
            implementationClass = "com.georgeci.moneysurfer.buildlogic.PlayPublisherConventionPlugin"
            displayName = "MoneySurfer Play Publisher Convention Plugin"
            description = "Applies and configures gradle-play-publisher with shared credential discovery."
        }
    }
}
