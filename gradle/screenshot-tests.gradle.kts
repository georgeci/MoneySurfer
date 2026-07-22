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

val referenceDir = "screenshots"
val roborazziOutputDir = layout.buildDirectory.dir("outputs/roborazzi")

// `record` is opt-in; every other invocation — local `check`, and the `qaAndroidHost`
// CI job — verifies and fails the build on a diff over the threshold.
val recordScreenshots = providers.gradleProperty("roborazzi.record").orNull == "true"

// Script plugins applied via `apply(from = …)` don't get the generated `libs.*`
// accessors, so the catalog is read through its extension instead.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("androidHostTestImplementation", libs.findBundle("screenshot-test").get())
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
    workingDir = projectDir
    // Roborazzi's capture entry points are JUnit 4, and AGP's host-test task runs
    // JUnit 4 by default. If a module ever switches this task to the JUnit Platform
    // (as the kotest-based feature modules do for `jvmTest`), the screenshot tests
    // would stop being discovered and the suite would pass by running nothing.
    // Fail loudly instead — the fix is to add `junit-vintage-engine` as runtimeOnly.
    doFirst {
        check(options !is org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions) {
            "$path runs on the JUnit Platform, which cannot discover the JUnit 4 " +
                "screenshot tests. Add org.junit.vintage:junit-vintage-engine as " +
                "androidHostTestRuntimeOnly, or keep this task on JUnit 4."
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
    doFirst {
        require(recordScreenshots) {
            "recordScreenshots must be run with -Proborazzi.record=true so the reference images are actually rewritten."
        }
    }
}

tasks.register("verifyScreenshots") {
    group = "verification"
    description = "Verifies rendered screens against the committed reference screenshots in $referenceDir/."
    dependsOn("testAndroidHostTest")
}
