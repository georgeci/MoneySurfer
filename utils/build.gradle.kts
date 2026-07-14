plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.utils"
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    // StringResourcePlaceholderTest scans every strings.xml in the repo at
    // runtime; declare them as inputs so editing one re-runs the gate instead
    // of hitting UP-TO-DATE.
    inputs.files(
        fileTree(rootDir) {
            include("**/strings.xml")
            exclude("**/build/**", "**/.git/**", "**/.gradle/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}
