// Roborazzi screenshot-test wiring for a feature module (issue #85).
//
// Applied from a feature module's build.gradle.kts:
//
//     apply(from = rootProject.file("gradle/screenshot-tests.gradle.kts"))
//
// Deliberately *not* the `io.github.takahirom.roborazzi` Gradle plugin: that
// plugin drives AGP's application/library variants, and every module here uses
// `com.android.kotlin.multiplatform.library`, whose test task is `androidHostTest`
// rather than `test<Variant>UnitTest`. Roborazzi's runtime is configured entirely
// through system properties, so the plugin's only real job — flipping
// record/verify and pointing at an output dir — is a few lines of wiring.
//
// Modes (see docs/testing/screenshot-tests.md):
//   ./gradlew :feature:x:recordScreenshots   → (re)write the committed references
//   ./gradlew :feature:x:testAndroidHostTest → verify against them (the CI default)
//
// This script handles dependencies and task wiring only. Two things it cannot do — a script
// plugin applied with `apply(from = …)` is compiled against the Gradle API alone, with none of
// the Kotlin or detekt plugin types on its classpath — so the applying module must also:
//
//   1. compile the shared capture harness into its host-test source set, in its `kotlin { }`:
//        sourceSets.named("androidHostTest") {
//            kotlin.srcDir(rootProject.file("gradle/screenshot-harness/kotlin"))
//        }
//   2. be listed in `screenshotHarnessProjects` in the root build.gradle.kts, which points
//      detekt at that same directory.
//
// The harness is shared source rather than a module because captures are only comparable
// across modules if every module renders through literally the same code, and no module can
// depend on another module's test source set.

val referenceDir = "screenshots"
val roborazziOutputDir = layout.buildDirectory.dir("outputs/roborazzi")
val roborazziResultDir = layout.buildDirectory.dir("test-results/roborazzi")

// `record` is opt-in; every other invocation — local `check`, and the `qaAndroidHost`
// CI job — verifies and fails the build on a diff over the threshold.
val recordScreenshots = providers.gradleProperty("roborazzi.record").orNull == "true"

// Script plugins applied via `apply(from = …)` don't get the generated `libs.*`
// accessors, so the catalog is read through its extension instead.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("androidHostTestImplementation", libs.findBundle("screenshot-test").get())
    // Roborazzi's capture entry points are JUnit 4. Modules whose host-test task runs on
    // the JUnit Platform (the kotest-based feature modules) would otherwise discover none
    // of them and pass by running nothing — see the guard on the test task below.
    add("androidHostTestRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
}

// AGP's KMP-library plugin registers the host-test task after this script is
// applied, so it has to be matched lazily rather than looked up by name.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    systemProperty("roborazzi.test.record", recordScreenshots)
    systemProperty("roborazzi.test.verify", !recordScreenshots)
    systemProperty("roborazzi.output.dir", roborazziOutputDir.get().asFile.path)
    // Reference images are addressed relative to the module directory, so the
    // committed path is `<module>/screenshots/<name>.png` regardless of where
    // Gradle happens to fork the test JVM from.
    systemProperty("roborazzi.record.filePathStrategy", "relativePathFromCurrentDirectory")
    systemProperty("roborazzi.result.dir", roborazziResultDir.get().asFile.path)
    workingDir = projectDir
    // Resolved into a plain `File` local *before* the action below closes over it. A lambda
    // that reads a script-level property captures the whole build script, which the
    // configuration cache cannot serialize.
    val resultDir = roborazziResultDir.get().asFile
    // A screenshot suite that discovers no tests passes silently, which is the one failure
    // mode that looks exactly like success — the JUnit 4 / JUnit Platform mismatch above is
    // how you get there. Roborazzi drops one result JSON per capture, so an empty result
    // directory after a run means nothing was captured. Fail on it.
    doLast {
        val captured = resultDir.listFiles { file: File -> file.extension == "json" }.orEmpty()
        check(captured.isNotEmpty()) {
            "$path captured no screenshots. The JUnit 4 capture tests were not discovered — " +
                "check that org.junit.vintage:junit-vintage-engine is on the host-test runtime " +
                "classpath if this task runs on the JUnit Platform."
        }
    }
    // Robolectric forks a heavier JVM than the plain kotest suites do.
    maxHeapSize = "2g"
    // The captured PNGs are inputs, not just outputs: a reference image edited
    // by hand must re-run verification.
    inputs.files(layout.projectDirectory.dir(referenceDir).asFileTree)
        .withPropertyName("screenshotReferences")
}

tasks.register("recordScreenshots") {
    group = "verification"
    description = "Records the Roborazzi reference screenshots into $referenceDir/ (run with -Proborazzi.record=true)."
    dependsOn("testAndroidHostTest")
    // Read into a local before the action closes over it — a lambda that touches a script-level
    // property captures the whole build script, which the configuration cache cannot serialize.
    val recording = recordScreenshots
    doFirst {
        require(recording) {
            "recordScreenshots must be run with -Proborazzi.record=true so the reference images are actually rewritten."
        }
    }
}

tasks.register("verifyScreenshots") {
    group = "verification"
    description = "Verifies rendered screens against the committed reference screenshots in $referenceDir/."
    dependsOn("testAndroidHostTest")
}
