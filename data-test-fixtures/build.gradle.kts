plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.data.fixtures"
    }

    sourceSets {
        commonMain.dependencies {
            // FirebaseConfig interface lives in :domain; tests bind their own impl.
            implementation(projects.domain)
            implementation(libs.kotlinx.coroutinesCore)
        }

        jvmMain.dependencies {
            // Emulator REST clients (reset / seed) use kotlinx-serialization to encode payloads;
            // HTTP itself goes through java.net.HttpURLConnection — no Ktor dependency.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
