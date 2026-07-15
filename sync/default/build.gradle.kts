plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.sync"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.datetime)
                implementation(libs.arrow.core)
                implementation(libs.kermit)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(projects.domain)
                implementation(projects.sync.api)
                implementation(libs.kotlinx.serialization.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.turbine)
                implementation(libs.kotlinx.coroutines.test)
                implementation(projects.syncTestFixtures)
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

        androidMain {
            dependencies {
                implementation(libs.androidx.work.runtime)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.testExt.junit)
            }
        }

        iosMain {
            dependencies {
            }
        }
    }
}

koinCompiler {
    compileSafety = false
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
