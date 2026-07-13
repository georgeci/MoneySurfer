package com.georgeci.moneysurfer.buildlogic

import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.github.triplet.gradle.play.PlayPublisherExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.util.Properties

/**
 * Applies `com.github.triplet.play` and configures it identically across
 * `:androidApp` / `:androidApp-offline`. Centralized so the two app modules
 * cannot drift on track / bundle defaults / credential resolution.
 *
 * Credential discovery (first match wins):
 *   1. `PLAY_SERVICE_ACCOUNT_JSON` env — **inline JSON contents** (matches the
 *      shape of the GitHub Actions secret used by the release workflow).
 *      Materialized to `<module>/build/play/service-account.json`.
 *   2. `PLAY_SERVICE_ACCOUNT_JSON_PATH` env — **file path** relative to repo
 *      root, for runners that prefer mounting a credentials file.
 *   3. `PLAY_SERVICE_ACCOUNT_JSON_PATH` from `local.properties` — same shape,
 *      for local dev convenience.
 *   4. Fallback `play-service-account.json` at repo root (gitignored).
 *
 * If no credentials are found the plugin's tasks are disabled so contributors
 * without Play access can still build locally.
 *
 * Both apps are still drafts in Play Console: until the first production
 * release goes live the API rejects `completed` releases on draft apps with
 * "Only releases with status draft may be created on draft app." The shared
 * `releaseStatus = DRAFT` here keeps internal-track uploads working; it should
 * be removed once both apps have at least one published prod release.
 */
@Suppress("unused")
class PlayPublisherConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.github.triplet.play")

        val credsFile = resolveServiceAccountFile()

        extensions.configure<PlayPublisherExtension> {
            enabled.set(credsFile != null)
            credsFile?.let { serviceAccountCredentials.set(it) }
            defaultToAppBundles.set(true)
            track.set("internal")
            releaseStatus.set(ReleaseStatus.DRAFT)
        }
    }

    private fun Project.resolveServiceAccountFile(): File? {
        val inlineJson = providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON").orNull
        if (!inlineJson.isNullOrBlank()) {
            val materialized = layout.buildDirectory
                .file("play/service-account.json")
                .get()
                .asFile
            materialized.parentFile.mkdirs()
            materialized.writeText(inlineJson)
            return materialized
        }

        val pathFromEnv = providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON_PATH").orNull
        val pathFromProps = rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.let { f ->
                Properties().apply { f.inputStream().use { load(it) } }
                    .getProperty("PLAY_SERVICE_ACCOUNT_JSON_PATH")
            }
        val resolvedPath = pathFromEnv ?: pathFromProps ?: "play-service-account.json"
        return rootProject.file(resolvedPath).takeIf { it.exists() }
    }
}
