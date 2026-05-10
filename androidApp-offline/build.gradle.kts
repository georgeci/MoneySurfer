import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kmpApp)
    id("ms.play-publisher")
}

android {
    namespace = "com.georgeci.moneysurfer.offline"

    defaultConfig {
        applicationId = "com.georgeci.moneysurfer.offline"
        versionName = "${versionName}-offline"
    }
}

dependencies {
    implementation(projects.composeAppOffline)
}

// Build-time guard: the offline build must never ship with networking permissions.
// AGP's manifest-merger output is the only authoritative place to look — a transitive
// AAR can inject `INTERNET` long after the source manifest has been written.
//
// For every variant we register a `verifyOfflineManifest<Variant>` task that parses the
// merged `AndroidManifest.xml` and fails if any forbidden permission survived. The task
// is wired as a dependency of `assemble<Variant>` / `bundle<Variant>` / `install<Variant>`
// via `tasks.matching { ... }.configureEach { ... }`, which is configuration-cache
// compatible (no `afterEvaluate`).
val forbiddenOfflinePermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
)

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val capitalized = variantName.replaceFirstChar { it.uppercase() }
        val verifyTask = tasks.register("verifyOfflineManifest$capitalized") {
            group = "verification"
            description = "Fail the build if the merged $variantName manifest contains forbidden " +
                "networking permissions."

            val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            val forbidden = forbiddenOfflinePermissions
            inputs.file(mergedManifest)
            // Track the policy itself as an input so the task reruns whenever the
            // forbidden list changes, even if the merged manifest is unchanged.
            inputs.property("forbiddenPermissions", forbidden)

            doLast {
                val manifestFile = mergedManifest.get().asFile
                val text = manifestFile.readText()
                // The manifest merger emits each `<uses-permission android:name="..."/>`
                // on its own; a regex over `android:name="..."` is enough — no full XML
                // parse required, and it survives attribute reordering.
                val nameRegex = Regex("""android:name\s*=\s*"([^"]+)"""")
                val present = nameRegex.findAll(text)
                    .map { it.groupValues[1] }
                    .filter { it in forbidden }
                    .toSortedSet()
                if (present.isNotEmpty()) {
                    val list = present.joinToString("\n  - ", prefix = "  - ")
                    throw GradleException(
                        """
                        |Offline build leaks forbidden networking permissions in the merged manifest:
                        |$list
                        |
                        |Merged manifest: ${manifestFile.absolutePath}
                        |
                        |Inspect the merger report next to the merged manifest (look for
                        |`manifest-merger-${variantName}-report.txt` under
                        |`androidApp-offline/build/outputs/logs/`) to identify the offending
                        |dependency, then either drop it or extend `tools:node="remove"`
                        |coverage in androidApp-offline/src/main/AndroidManifest.xml.
                        """.trimMargin(),
                    )
                }
            }
        }
        // Wire the verify task into every shippable artifact for this variant. Use
        // `tasks.matching { ... }.configureEach { ... }` instead of `afterEvaluate` so
        // the wiring participates in the configuration cache. `inputs.file(mergedManifest)`
        // already pulls in `process${capitalized}Manifest` as an upstream dependency.
        // Wire into both the user-facing aggregator tasks AND the per-step bundle
        // pipeline. gradle-play-publisher's `publishReleaseBundle` consumes the
        // signed bundle artifact via the AGP Variant API directly — it never
        // invokes `bundle<Variant>`, so without `package*Bundle` / `sign*Bundle`
        // here a Play upload would skip the permission check.
        val consumerNames = setOf(
            "assemble$capitalized",
            "bundle$capitalized",
            "install$capitalized",
            "package${capitalized}Bundle",
            "sign${capitalized}Bundle",
        )
        tasks.matching { it.name in consumerNames }.configureEach {
            dependsOn(verifyTask)
        }
    }
}

// Sonar Gradle plugin (≤6.0.x) still references the legacy AGP `AppExtension`,
// which AGP 9 removed. Skip this thin entry-point module — its sources are just
// the Activity host; real code analyzed via :composeApp / :feature / :domain.
sonar {
    isSkipProject = true
}
