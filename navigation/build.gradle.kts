plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.navigation"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material3.adaptive.navigation3)
                implementation(libs.compose.material3.adaptive.navigation.suite)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.ui)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.navigation3.resultstate)
                implementation(libs.kotlinx.serialization.core)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.koin.annotations)
                implementation(projects.domain)
                implementation(projects.sync.api)
                implementation(projects.uikit)
                implementation(libs.kermit)
            }
        }

        commonTest {
            dependencies {
                implementation(projects.domainTestFixtures)
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                // Virtual time for the periodic-sync gate: `flatMapLatest` over a hot source is
                // racy without a controlled dispatcher.
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                // RouteSerializationTest round-trips every route through `navKeySerializersModule`;
                // JSON stands in for the saved-state format, which needs an Android runtime.
                implementation(libs.kotlinx.serialization.json)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "moneysurfer.navigation.generated.resources"
}

koinCompiler {
    compileSafety = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
