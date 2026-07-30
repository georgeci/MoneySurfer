package com.georgeci.moneysurfer.data.remote

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import dev.gitlive.firebase.FirebaseOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * App Check for the desktop host.
 *
 * Android attests with Play Integrity and iOS with App Attest. The JVM has neither: gitlive's
 * `firebase-java-sdk` ships the App Check core but not a single provider, so the only thing a
 * desktop client can present is a **debug secret** registered by hand under
 * App Check → Apps → Manage debug tokens. That is by design — desktop is a developer-only host,
 * and a debug secret is exactly as trusted as the developer holding it.
 *
 * Wiring is opt-in: without `MS_FIREBASE_APPCHECK_DEBUG_TOKEN` nothing is installed and the host
 * behaves as it did before. Against the emulator it is skipped entirely — the emulator does not
 * verify App Check tokens and there is no project to register a secret with.
 *
 * The provider SPI is small and fully public (`AppCheckProvider.getToken()`,
 * `AppCheckProviderFactory.create()`, and `AppCheckToken` as an abstract class), so none of this
 * reaches into the SDK's internal packages.
 */
internal fun installDesktopAppCheck(
    options: FirebaseOptions,
    env: (String) -> String? = System::getenv,
    exchange: DebugTokenExchange = DebugTokenExchange(::postJson),
) {
    val debugToken = env(ENV_APPCHECK_DEBUG_TOKEN)?.takeIf { it.isNotBlank() } ?: return
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(
            DesktopAppCheckProviderFactory(
                options = options,
                debugToken = debugToken,
                exchange = exchange,
            ),
        )
}

/**
 * The one network call this file makes, injected so the exchange can be tested without
 * reaching Google. Takes a URL and a JSON body, returns the response body.
 */
internal fun interface DebugTokenExchange {
    fun post(url: String, body: String): String
}

internal class DesktopAppCheckProviderFactory(
    private val options: FirebaseOptions,
    private val debugToken: String,
    private val exchange: DebugTokenExchange,
) : AppCheckProviderFactory {

    override fun create(firebaseApp: FirebaseApp): AppCheckProvider =
        DesktopAppCheckProvider(options, debugToken, exchange)
}

internal class DesktopAppCheckProvider(
    private val options: FirebaseOptions,
    private val debugToken: String,
    private val exchange: DebugTokenExchange,
) : AppCheckProvider {

    override fun getToken(): Task<AppCheckToken> = Tasks.call { exchangeDebugToken() }

    internal fun exchangeDebugToken(): AppCheckToken {
        val url = "$APP_CHECK_BASE_URL/projects/${projectNumberOf(options.applicationId)}" +
            "/apps/${options.applicationId}:exchangeDebugToken?key=${options.apiKey}"
        val response = json.decodeFromString<DebugTokenResponse>(
            exchange.post(url, """{"debugToken":"$debugToken"}"""),
        )
        return DesktopAppCheckToken(
            token = response.token,
            expiresAtMillis = System.currentTimeMillis() + secondsOf(response.ttl) * MILLIS_PER_SECOND,
        )
    }
}

/**
 * The App Check REST path is keyed by project *number*, not project id, and the number is already
 * the second segment of the app id (`1:<projectNumber>:android:<hash>`). Parsing it here keeps the
 * desktop host on the three `MS_FIREBASE_*` variables it already documents.
 */
internal fun projectNumberOf(applicationId: String): String =
    applicationId.split(':').getOrNull(APP_ID_PROJECT_NUMBER_SEGMENT)
        ?.takeIf { it.isNotBlank() }
        ?: error(
            "Cannot read the project number from MS_FIREBASE_APP_ID='$applicationId' — expected " +
                "the Firebase app id form 1:<projectNumber>:<platform>:<hash>.",
        )

/** Google returns the lifetime as a protobuf duration string, e.g. `3600s`. */
internal fun secondsOf(ttl: String): Long = ttl.removeSuffix("s").toLongOrNull() ?: 0L

@Serializable
internal data class DebugTokenResponse(val token: String, val ttl: String = "")

private class DesktopAppCheckToken(
    private val token: String,
    private val expiresAtMillis: Long,
) : AppCheckToken() {
    override fun getToken(): String = token
    override fun getExpireTimeMillis(): Long = expiresAtMillis
}

private val json = Json { ignoreUnknownKeys = true }

private fun openJsonPost(url: String): HttpURLConnection {
    val connection = try {
        URI(url).toURL().openConnection() as HttpURLConnection
    } catch (error: IOException) {
        throw HttpRequestException("Cannot open $url", error)
    } catch (error: IllegalArgumentException) {
        throw HttpRequestException("Malformed URL: $url", error)
    }
    connection.requestMethod = "POST"
    connection.connectTimeout = TIMEOUT_MILLIS
    connection.readTimeout = TIMEOUT_MILLIS
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    return connection
}

private fun postJson(url: String, body: String): String {
    val connection = openJsonPost(url)
    return try {
        connection.outputStream.use { it.write(body.toByteArray()) }
        val status = connection.responseCode
        if (status !in HTTP_OK_RANGE) {
            // The body carries Google's reason — most often that the debug secret is not
            // registered on this project, which is otherwise indistinguishable from a network fault.
            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw HttpRequestException("HTTP $status from App Check debug exchange: $detail")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (error: IOException) {
        throw HttpRequestException("App Check debug exchange failed", error)
    } finally {
        connection.disconnect()
    }
}

private const val ENV_APPCHECK_DEBUG_TOKEN = "MS_FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val APP_CHECK_BASE_URL = "https://firebaseappcheck.googleapis.com/v1"
private const val APP_ID_PROJECT_NUMBER_SEGMENT = 1
private const val MILLIS_PER_SECOND = 1_000L
private const val TIMEOUT_MILLIS = 15_000
private val HTTP_OK_RANGE = 200..299
