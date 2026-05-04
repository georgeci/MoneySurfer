plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kmp.plugin)
    compileOnly(libs.kmp.plugin.lib)
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
    }
}
