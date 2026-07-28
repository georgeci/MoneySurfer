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

    // Adding the manual jvm+android edge below opts out of the auto-applied
    // source-set hierarchy, so re-apply the default template explicitly to keep
    // the iOS (native) intermediates wired to commonMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM and Android share the same javax.crypto / JCA backend, so the
        // BackupCrypto actual lives once in this intermediate set rather than
        // as two byte-identical twins. Only the iOS actual (CommonCrypto) differs.
        val jvmAndroidMain = create("jvmAndroidMain") {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependsOn(jvmAndroidMain)
        }
        androidMain {
            dependsOn(jvmAndroidMain)
        }

        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.okio)
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
