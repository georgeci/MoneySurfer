plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.data.remote"
    }

    // Adding the manual jvm+android edge below opts out of the auto-applied
    // source-set hierarchy, so re-apply the default template explicitly to keep
    // the iOS (native) intermediates wired to commonMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM and Android share the same java.net HTTP stack, so the plain-GET actual behind the
        // FX fetch lives once in this intermediate set rather than as two identical twins. Only
        // the iOS actual (NSURLSession) differs.
        val jvmAndroidMain by creating {
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
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.datetime)
                implementation(libs.arrow.core)
                implementation(libs.kermit)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.gitlive.firebase.common)
                implementation(libs.gitlive.firebase.analytics)
                implementation(libs.gitlive.firebase.auth)
                implementation(libs.gitlive.firebase.firestore)
                implementation(projects.domain)
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
            }
        }

        androidMain {
            dependencies {
                implementation(libs.gitlive.firebase.crashlytics)
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.analytics)
                implementation(libs.firebase.auth)
                implementation(libs.firebase.crashlytics)
                implementation(libs.firebase.firestore)
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
                implementation(libs.gitlive.firebase.crashlytics)
            }
        }

        jvmMain {
            dependencies {}
        }
    }
}

koinCompiler {
    compileSafety = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
