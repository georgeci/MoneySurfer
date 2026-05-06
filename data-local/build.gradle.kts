plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.data.local"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.arrow.core)
                implementation(libs.kermit)
                implementation(projects.domain)
                implementation(projects.sync.api)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.turbine)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.fixture.monkey.kotlin)
                implementation(projects.domainTestFixtures)
            }
        }

        androidMain {
            dependencies {}
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.testExt.junit)
            }
        }

        iosMain {
            dependencies {}
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

lint {
    disable += "RestrictedApi"
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
