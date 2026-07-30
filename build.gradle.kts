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
    alias(libs.plugins.kotlinJvm) apply false
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
            // Claude Code keeps its worktrees under `.claude/worktrees/`, inside the main
            // checkout: without this every file is its own duplicate, once per live worktree.
            "**/.claude/**",
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

// Modules with no test sources of their own, plus test scaffolding. They are
// deliberately absent from the `kover(...)` aggregation below, so Sonar receives
// no coverage data for them; without an explicit exclusion that reads as a real
// 0% and drags the headline number down (issue #272).
//
// Applied per-module in `subprojects { afterEvaluate { ... } }` rather than as a
// single root `sonar.coverage.exclusions`: coverage exclusion patterns are matched
// against each file's path *relative to its own module's baseDir*, and the root
// property is inherited by every submodule — so a root-level `shared/**`
// would be matched against `src/commonMain/kotlin/...` inside that module and never
// hit anything. `**/*` scoped to the module itself is unambiguous.
//
// This list is a TODO, not a target state: as a module gains tests, add it to
// `kover(...)` and delete it from here.
val coverageExcludedProjects = setOf(
    ":shared",
    ":sync:no-op",
    ":feature",
    // Container project for `app-config:api` / `app-config:default` / `app-config:remote`;
    // no sources of its own.
    ":app-config",
    ":androidApp",
    ":androidApp-offline",
    ":sync-test-fixtures",
    ":domain-test-fixtures",
    ":data-test-fixtures",
    // Test-only module: aggregated into Kover so its integration tests credit the
    // modules they exercise, but its own sources aren't production code to cover.
    ":integration-test",
    // Build tooling: detekt rules run against the build, they are not shipped.
    ":detekt-rules",
)

// Modules that compile the shared Roborazzi harness from `gradle/screenshot-harness/` into their
// host-test source set (see gradle/screenshot-tests.gradle.kts). That directory sits outside every
// module's `src/`, so detekt has to be pointed at it explicitly — once per module that actually
// compiles it, rather than for the whole build.
val screenshotHarnessProjects = setOf(
    ":uikit",
    ":feature:dashboard",
    ":feature:login",
)

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
            // Plain-JVM layout — only `:detekt-rules` uses it.
            "src/main/kotlin",
            "src/test/kotlin",
        )
        if (path in screenshotHarnessProjects) {
            source.from(rootProject.file("gradle/screenshot-harness/kotlin"))
        }
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
        // The project's own rules, minus the module that defines them — depending
        // on itself would be a dependency cycle. It is still linted by the
        // built-in rules above.
        if (path != ":detekt-rules") {
            "detektPlugins"(project(":detekt-rules"))
        }
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
            // Coverage exclusions, module-relative like `sonar.sources` above:
            //  - whole module for the ones outside the Kover aggregation;
            //  - every native source set everywhere else. Kover instruments JVM
            //    bytecode, so an iOS `actual` can never carry coverage data, while
            //    `sonar.sources` does hand those files to Sonar — which then scores
            //    them 0% and drags "Coverage on New Code" down on every iOS-side
            //    change (same failure shape as issue #272).
            //
            // Derived from `mainDirs` rather than hard-coding `src/iosMain`, so the
            // per-target layouts already listed in `mainCandidates`
            // (`iosArm64Main`, `iosX64Main`, `iosSimulatorArm64Main`) are covered
            // the day a module starts using one.
            val coverageExclusions = when {
                path in coverageExcludedProjects -> listOf("**/*")
                else -> mainDirs.filter { it.startsWith("src/ios") }.map { "$it/**/*" }
            }
            if (coverageExclusions.isNotEmpty()) {
                property("sonar.coverage.exclusions", coverageExclusions.joinToString(","))
            }
        }
    }
}

// Kover aggregation — the merged report at build/reports/kover/report.xml feeds
// Codecov, the Pages HTML report, and SonarCloud's coverage import (issue #272).
//
// Every module that owns test sources belongs here. A module with tests that is
// *missing* from this list contributes no coverage at all, which SonarCloud then
// reports as 0% on any new code in it — that was one of the three causes behind
// the permanent "0.0% Coverage on New Code" (issue #272). Untested production
// modules are deliberately left out and named in `sonar.coverage.exclusions`
// below instead, so their absence is explicit rather than silent.
dependencies {
    kover(projects.composeApp)
    kover(projects.composeAppOffline)
    kover(projects.domain)
    kover(projects.appConfig.api)
    kover(projects.appConfig.default)
    kover(projects.appConfig.remote)
    kover(projects.dataLocal)
    kover(projects.dataRemote)
    kover(projects.syncSurfer)
    kover(projects.sync.api)
    kover(projects.sync.default)
    kover(projects.uikit)
    kover(projects.feature.login)
    kover(projects.feature.goal)
    kover(projects.feature.account)
    kover(projects.feature.budget)
    kover(projects.feature.category)
    kover(projects.feature.dashboard)
    kover(projects.feature.settings)
    kover(projects.feature.transaction)
    kover(projects.feature.workspace)
    kover(projects.utils)
    kover(projects.navigation)
    // Test-only module: contributes no production classes of its own, but its
    // integration tests exercise the modules above, so its coverage data must be
    // merged in or that exercise is invisible to the report.
    kover(projects.integrationTest)
}

// Report filters for the merged report — they live on the root project because that
// is where `koverXmlReport` / `koverHtmlReport` run, so one block covers Codecov,
// the Pages HTML report and Sonar's coverage import at once.
//
// Everything excluded here is code no test can execute, not code that is merely
// untested: leaving it in reports a permanent, unfixable deficit and hides the
// coverage that is actually movable. Untestable-by-platform code (`src/iosMain`) is
// excluded on the Sonar side above instead — it never reaches the Kover report to
// begin with.
kover {
    reports {
        filters {
            excludes {
                // 235 `@Preview` composables across 134 files, all in `commonMain`, so they
                // compile into the JVM target and count as uncovered forever. Nothing calls
                // them: the Roborazzi harness in gradle/screenshot-harness/ mounts the
                // component and `SurferComponentPreview`, never the `*Preview()` function.
                // Wildcard covers both annotations in use — `androidx.compose.ui.tooling.
                // preview.Preview` and the desktop `androidx.compose.desktop.…` one.
                annotatedBy("*.Preview")
                // Lambda holders synthesised by the Compose compiler — an artifact of
                // compilation, with no source line a test could target.
                classes("*ComposableSingletons*")
                // Room codegen — `MoneySurferDatabase_Impl`, `SyncDatabase_Impl` and the
                // `*Dao_Impl` classes, plus their `$1`/`$Companion` inner classes. No
                // hand-written class in the repo ends in `_Impl`.
                //
                // The trailing `*` is load-bearing and was verified against the merged
                // report: the tail-anchored `"*_Impl"` form left every one of these classes
                // in report.xml, `"*_Impl*"` removes all 17. Same for the other patterns
                // here — check the class is actually gone from report.xml after editing this
                // list rather than trusting the pattern to read correctly.
                classes("*_Impl*")
                // AGP codegen. Not `androidGeneratedClasses()`: that shortcut also drops
                // every `*Activity` / `*Fragment`, which would silently swallow real code
                // the day a module gains one.
                classes("*.BuildConfig")
                // Desktop entry point: `fun main()` in composeApp/src/jvmMain — starts the
                // real application window, so no test can call it.
                classes("com.georgeci.moneysurfer.MainKt")
            }
        }
    }
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

        // Compose APIs legitimately expose independent state, callback, and styling slots.
        // Keep S107 active everywhere else: DAO methods, use cases, and ordinary Kotlin APIs
        // still benefit from the seven-parameter limit.
        val composeLongParameterListExclusions = mapOf(
            "composeConfirmDialog" to "**/SurferConfirmDialog.kt",
            "composeTextField" to "**/SurferTextField.kt",
            "composeGoalCard" to "**/SurferGoalCard.kt",
            "composeAccountChooser" to "**/AccountChooserBottomSheet.kt",
            "composeTransactionCreation" to "**/TransactionCreationScreen.kt",
        )
        property(
            "sonar.issue.ignore.multicriteria",
            composeLongParameterListExclusions.keys.joinToString(","),
        )
        composeLongParameterListExclusions.forEach { (criterion, resourceKey) ->
            property("sonar.issue.ignore.multicriteria.$criterion.ruleKey", "kotlin:S107")
            property("sonar.issue.ignore.multicriteria.$criterion.resourceKey", resourceKey)
        }

        // Coverage import (issue #272). Kover emits a JaCoCo-format XML — verified:
        // the merged report carries <sourcefile>/<line> elements, which is what
        // Sonar's JaCoCo sensor actually reads (a report with only <class>/<method>
        // counters would import nothing).
        //
        // The path MUST be absolute. This property is inherited by every Sonar
        // submodule, and a relative path re-resolves against each module's own
        // baseDir — so `build/reports/kover/report.xml` would be looked up as
        // `<module>/build/reports/kover/report.xml`, which does not exist for any
        // module. Every module then reports 0%. Point them all at the one merged
        // report at the root instead.
        //
        // The report is produced by the `junit` CI job (`qaCommon qaAndroidHost`
        // → koverXmlReport) and handed to the `sonar` job as the `kover-xml`
        // artifact — see .github/workflows/ci.yml. Locally: ./gradlew koverXmlReport.
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            rootProject.layout.buildDirectory.file("reports/kover/report.xml").get().asFile.absolutePath,
        )

        // Coverage exclusions for untested modules are set per-module in the
        // `subprojects { afterEvaluate { ... } }` block above — see the comment on
        // `coverageExcludedProjects` for why they cannot live here.
    }
}
