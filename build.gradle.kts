plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.androidLint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotest.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.android.built.in1.kotlin) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.moduleGraph)
}

moduleGraphConfig {
    readmePath.set("$rootDir/docs/module-graph.md")
    heading.set("# Module Dependency Graph")
    // Hide root-project edges that come from `kover(...)` aggregation —
    // those are coverage wiring, not architectural dependencies.
    excludedConfigurationsRegex.set(".*kover.*")
}

apply(from = "gradle/qa.gradle.kts")

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("$projectDir/config/detekt/baseline.xml")
        buildUponDefaultConfig = true
        autoCorrect = true
        parallel = true
        source.setFrom(
            "src/commonMain/kotlin",
            "src/commonTest/kotlin",
            "src/androidMain/kotlin",
            "src/androidHostTest/kotlin",
            "src/iosMain/kotlin",
            "src/jvmMain/kotlin",
            "src/jvmTest/kotlin",
        )
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    // Per-module Sonar source discovery for KMP layouts. Setting `sonar.sources`
    // only at root made SonarCloud miss subproject sources (it saw ~3 dirs).
    afterEvaluate {
        val mainCandidates = listOf(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/iosMain/kotlin",
            "src/iosArm64Main/kotlin",
            "src/iosX64Main/kotlin",
            "src/iosSimulatorArm64Main/kotlin",
            "src/jvmMain/kotlin",
            "src/main/kotlin",
            "src/main/java",
        )
        val testCandidates = listOf(
            "src/commonTest/kotlin",
            "src/androidHostTest/kotlin",
            "src/androidUnitTest/kotlin",
            "src/androidInstrumentedTest/kotlin",
            "src/iosTest/kotlin",
            "src/jvmTest/kotlin",
            "src/test/kotlin",
            "src/test/java",
        )
        val mainDirs = mainCandidates.filter { file(it).isDirectory }
        val testDirs = testCandidates.filter { file(it).isDirectory }
        extensions.findByType(org.sonarqube.gradle.SonarExtension::class.java)?.properties {
            if (mainDirs.isNotEmpty()) {
                property("sonar.sources", mainDirs.joinToString(","))
            }
            if (testDirs.isNotEmpty()) {
                property("sonar.tests", testDirs.joinToString(","))
            }
        }
    }
}

dependencies {
    kover(projects.composeApp)
    kover(projects.composeAppOffline)
    kover(projects.domain)
    kover(projects.dataLocal)
    kover(projects.dataRemote)
    kover(projects.syncSurfer)
    kover(projects.sync.default)
    kover(projects.uikit)
    kover(projects.feature.login)
}

sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "georgeci")
        property("sonar.projectKey", "georgeci_MoneySurfer")
        property("sonar.projectName", "MoneySurfer")

        // Source/test directory discovery happens per-subproject (see
        // `subprojects { afterEvaluate { ... } }` above) so SonarCloud sees
        // every KMP module instead of just the root.

        property("sonar.sourceEncoding", "UTF-8")

        property(
            "sonar.kotlin.detekt.reportPaths",
            subprojects.joinToString(",") {
                "${it.projectDir}/build/reports/detekt/detekt.xml"
            },
        )

        property(
            "sonar.exclusions",
            "**/build/**,**/generated/**,iosApp/**,scripts/**,md/**,docs/**,firestore-tests/**,**/config/detekt/**",
        )
    }
}
