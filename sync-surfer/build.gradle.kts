plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.sync.impl"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.arrow.core)
                implementation(libs.kermit)
                implementation(libs.gitlive.firebase.common)
                implementation(libs.gitlive.firebase.analytics)
                implementation(libs.gitlive.firebase.auth)
                implementation(libs.gitlive.firebase.firestore)
                implementation(projects.domain)
                implementation(projects.sync.api)
                implementation(projects.sync.default)
                implementation(projects.dataLocal)
                implementation(projects.dataRemote)
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
                // commonTest, not jvmTest: the specs here also compile for androidHostTest, and the
                // shared facade fakes (FakeSyncSettings, FakeHostCapabilities) are used from common.
                implementation(projects.domainTestFixtures)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.fixture.monkey.kotlin)
                implementation(projects.dataTestFixtures)
            }
        }

        androidMain {
            dependencies {
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.analytics)
                implementation(libs.firebase.auth)
                implementation(libs.firebase.firestore)
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
            dependencies {}
        }
    }
}

koinCompiler {
    compileSafety = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    systemProperty("kotest.tags.exclude", "emulator")
}

tasks.register<Test>("emulatorTest") {
    description = "Runs :sync-impl tests tagged @emulator against a running Firebase Emulator Suite."
    group = "verification"
    useJUnitPlatform()
    systemProperty("kotest.tags.include", "emulator")
    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    shouldRunAfter(jvmTest)
}
