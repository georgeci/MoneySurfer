// Lock the root buildscript classpath *before* the `plugins { }` block resolves
// it, so the shared Gradle plugins declared here (AGP, Kotlin, Compose, detekt,
// …) are pinned. The `allprojects { }` activation below runs too late for the
// root project's own classpath — see docs/security/supply-chain.md.
buildscript {
    configurations.named("classpath") {
        resolutionStrategy.activateDependencyLocking()
    }
}

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
    alias(libs.plugins.cpd)
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

// Copy-paste detection (same engine SonarCloud uses for Kotlin via PMD CPD).
// Run locally before commit: `./gradlew cpdCheck`.
// Reports: build/reports/cpd/cpdCheck.{xml,txt}.
// See ai/skills/cpd-rules.md for usage rules.
cpd {
    language = "kotlin"
    minimumTokenCount = 100
    toolVersion = "7.7.0"
}

tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
    description = "Detects copy-paste duplication across all Kotlin sources."
    group = "verification"
    // Blocking gate: fail the build on new duplication. The existing backlog was
    // refactored away (issue #134); keep it clean rather than re-muting this.
    ignoreFailures = false
    reports {
        xml.required.set(true)
        text.required.set(true)
    }
    source = fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude(
            "**/build/**",
            "**/generated/**",
            "**/*Test*/**",
            "iosApp/**",
            "iosAppOffline/**",
            "**/.gradle/**",
            "**/.idea/**",
            // Platform `actual`-style twins: the android and ios FirebaseCrashReporter
            // are byte-identical because they compile against the same GitLive
            // multiplatform API. Sharing them needs an android+ios intermediate source
            // set (jvm uses NoOpCrashReporter), which isn't worth it for ~30 lines.
            "**/repository/FirebaseCrashReporter.kt",
        )
    }
}

// --- Supply-chain hardening: dependency locking (issue #158) -----------------
// Pin every resolved transitive version so a mutable/republished transitive
// (or a plugin-portal compromise that floats a version) cannot silently drift
// into a release or CI build. Locking only enforces where a `gradle.lockfile`
// exists; regenerate after any dependency change with:
//   ./gradlew resolveAndLockAll --write-locks --no-configuration-cache
// See docs/security/supply-chain.md for the full workflow (incl. the macOS-host iOS /
// Android-release configs and the remaining checksum-verification follow-up).
//
// Desktop artifacts from Compose (`skiko`, `compose.desktop`) encode the host
// OS + arch in their *module name* (…-macos-arm64 vs …-linux-x64). A single
// committed lockfile must stay valid on both the macOS and Ubuntu CI runners,
// so these host-variant families are excluded from the lock state rather than
// pinned to whichever host generated the file.
val hostVariantDependencies = listOf(
    "org.jetbrains.skiko:*",
    "org.jetbrains.compose.desktop:*",
)

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        ignoredDependencies.addAll(hostVariantDependencies)
    }

    // KGP 2.4's ABI-validation support declares its compat classpath with a
    // *version range* rather than the Kotlin version the build is on:
    //   kotlin-build-tools-impl:{strictly [2.4.0-Beta2, 2.5.0)}
    // Gradle resolves that to the highest published version in the range —
    // prereleases included — so the whole compat classpath (build-tools-impl,
    // compiler-embeddable, daemon-client, script-runtime, tooling-core, …)
    // floats on its own and rewrites ~25 lockfiles on an unrelated relock,
    // burying the lines a reviewer actually needs to check. A prerelease Kotlin
    // drifting into the build classpath is precisely what locking exists to stop.
    //
    // ABI validation is not used here (no `abiValidation { }`, no `.api` dumps),
    // but `enabled` already defaults to false and the configuration is created
    // and resolved regardless — so disabling it changes nothing. Pin the range
    // to the catalog's Kotlin version instead; `strictly` still holds because
    // that version lies inside the declared range. Everything else on the
    // classpath is transitive through `-impl` and follows it back down.
    // See docs/security/supply-chain.md for the full rationale.
    configurations.matching { it.name == "kotlinAbiValidationCompatClasspath" }.configureEach {
        val kotlinVersion = rootProject.libs.versions.kotlin.get()
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-build-tools-impl:$kotlinVersion")
    }

    // `lockAllConfigurations()` covers project dependency configurations but not
    // the buildscript classpath — where the Gradle plugins applied via the
    // `plugins { }` DSL (Kotlin, KSP, koin-compiler, …) actually resolve. Lock it
    // too so a compromised plugin republish cannot float into a build. The
    // pinned versions land in a separate `buildscript-gradle.lockfile`.
    buildscript {
        configurations.named("classpath") {
            resolutionStrategy.activateDependencyLocking()
        }
    }

    // `./gradlew resolveAndLockAll --write-locks --no-configuration-cache`
    // resolves every resolvable configuration in one pass so the lockfiles are
    // written in a single invocation. Kept out of the configuration cache
    // because it filters configurations at execution time.
    tasks.register("resolveAndLockAll") {
        group = "verification"
        description = "Resolves every configuration to (re)write the dependency lockfiles."
        notCompatibleWithConfigurationCache("Resolves configurations at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "Run resolveAndLockAll with --write-locks to (re)generate lockfiles."
            }
        }
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { it.incoming.artifactView { lenient(true) }.artifacts.artifacts }
        }
    }
}

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
            "src/jvmAndroidMain/kotlin",
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
