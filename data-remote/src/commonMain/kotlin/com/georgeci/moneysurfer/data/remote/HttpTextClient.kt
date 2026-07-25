package com.georgeci.moneysurfer.data.remote

/**
 * One plain HTTP GET returning the response body as text.
 *
 * Deliberately not a client library: the only non-Firebase network call in the app is the FX rate
 * fetch, and a Ktor client plus a per-platform engine would add four artifacts and their transitive
 * closure to every target for a single unauthenticated GET. `data-test-fixtures` already talks to
 * the Firebase emulator through `HttpURLConnection` on the same reasoning.
 *
 * Throws [HttpRequestException] for transport failures and non-2xx responses; callers are expected
 * to treat that as "no answer", not as a fatal error.
 */
internal expect suspend fun httpGetText(url: String): String

/** Transport-level failure of [httpGetText] — a typed stand-in for each platform's own exceptions. */
internal class HttpRequestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
