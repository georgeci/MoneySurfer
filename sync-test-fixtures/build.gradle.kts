plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.sync.fixtures"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(projects.sync.api)
            implementation(projects.sync.default)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
        }
    }
}
