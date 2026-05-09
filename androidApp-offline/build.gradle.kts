import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.android.built.in1.kotlin)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kmpApp)
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
// For every variant we wire a `verifyOfflineManifest<Variant>` task that parses the
// merged `AndroidManifest.xml` and fails if any forbidden permission survived. The
// task is hooked into `preBuild` of the same variant so `assemble` / `bundle` /
// `install` all pick it up automatically.
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
                        |Run `./gradlew :androidApp-offline:processOffline${capitalized}Manifest`,
                        |inspect the merger report next to the merged manifest, and either drop the
                        |offending dependency or extend `tools:node="remove"` coverage in
                        |androidApp-offline/src/main/AndroidManifest.xml.
                        """.trimMargin(),
                    )
                }
            }
        }
        // Hook into every shippable artifact for this variant. `inputs.file(mergedManifest)`
        // already makes the verify task depend on `process${capitalized}Manifest`, so we
        // only need to wire the consumers — `assemble`, `bundle`, `install` — to require it.
        afterEvaluate {
            listOf(
                "assemble$capitalized",
                "bundle$capitalized",
                "install$capitalized",
            ).forEach { name ->
                tasks.findByName(name)?.dependsOn(verifyTask)
            }
        }
    }
}

// Sonar Gradle plugin (≤6.0.x) still references the legacy AGP `AppExtension`,
// which AGP 9 removed. Skip this thin entry-point module — its sources are just
// the Activity host; real code analyzed via :composeApp / :feature / :domain.
sonar {
    isSkipProject = true
}
