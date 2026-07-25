package com.georgeci.moneysurfer.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** `HttpURLConnection` defaults to *no* timeout, so both halves have to be set explicitly. */
private const val TIMEOUT_MILLIS = 15_000
private val HTTP_OK_RANGE = 200..299

/**
 * JVM and Android share the same `java.net` stack, so the actual lives once here rather than as
 * two byte-identical twins — the same reasoning as `data-local`'s `BackupCrypto`.
 */
internal actual suspend fun httpGetText(url: String): String = withContext(Dispatchers.IO) {
    val connection = try {
        (URI(url).toURL().openConnection() as HttpURLConnection)
    } catch (error: IOException) {
        throw HttpRequestException("Cannot open $url", error)
    } catch (error: IllegalArgumentException) {
        throw HttpRequestException("Malformed URL: $url", error)
    }
    connection.requestMethod = "GET"
    connection.connectTimeout = TIMEOUT_MILLIS
    connection.readTimeout = TIMEOUT_MILLIS
    connection.setRequestProperty("Accept", "application/json")
    try {
        val status = connection.responseCode
        if (status !in HTTP_OK_RANGE) {
            throw HttpRequestException("HTTP $status from $url")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (error: IOException) {
        throw HttpRequestException("GET $url failed", error)
    } finally {
        connection.disconnect()
    }
}
