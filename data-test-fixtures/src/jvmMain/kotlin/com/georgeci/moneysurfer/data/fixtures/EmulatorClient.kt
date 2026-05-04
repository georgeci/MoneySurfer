package com.georgeci.moneysurfer.data.fixtures

import java.net.HttpURLConnection
import java.net.URI

/**
 * Thin REST client over the Firebase Emulator Suite admin endpoints.
 *
 * Used by tests for:
 * - readiness probing (`isReady()`) before kicking SDK calls,
 * - state cleanup between tests (`reset()`),
 * - direct seeding when SDK-level bootstrap is too slow.
 *
 * Intentionally uses `java.net.HttpURLConnection` — no Ktor dependency. The calls
 * are blocking; tests live on `Dispatchers.IO` or run-blocking, so a heavyweight
 * async client gives nothing here.
 */
class EmulatorClient(
    private val config: EmulatorFirebaseConfig = EmulatorFirebaseConfig(),
    private val projectId: String = EmulatorFirebaseConfig.DEFAULT_PROJECT_ID,
) {

    /**
     * Returns `true` once both Auth and Firestore emulators respond. Polls with
     * exponential backoff until [timeoutMillis]. Use as a Kotest `BeforeSpec` hook
     * so tests don't fire SDK calls into a still-booting emulator and get
     * `Connection refused`.
     */
    fun waitUntilReady(timeoutMillis: Long = DEFAULT_READY_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var delay = INITIAL_POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            if (isReady()) return true
            Thread.sleep(delay)
            delay = (delay * 2).coerceAtMost(MAX_POLL_INTERVAL_MS)
        }
        return false
    }

    fun isReady(): Boolean = ping(authBase()) && ping(firestoreBase())

    /**
     * Wipes all Auth users + all Firestore documents from this project. Called
     * between tests to keep state hermetic. Security rules are NOT reloaded —
     * the emulator picks those up automatically when `firestore.rules` changes
     * on disk.
     */
    fun reset() {
        delete("${authBase()}/emulator/v1/projects/$projectId/accounts")
        delete("${firestoreBase()}/emulator/v1/projects/$projectId/databases/(default)/documents")
    }

    private fun authBase(): String = "http://${config.host}:${config.auth}"
    private fun firestoreBase(): String = "http://${config.host}:${config.firestore}"

    private fun ping(url: String): Boolean = try {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = PROBE_TIMEOUT_MS
            readTimeout = PROBE_TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            // Any response (even 404 on root) means the listener is up.
            conn.responseCode in 200..599
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        false
    }

    private fun delete(url: String) {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = REST_TIMEOUT_MS
            readTimeout = REST_TIMEOUT_MS
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer owner")
        }
        try {
            val code = conn.responseCode
            check(code in 200..299) { "DELETE $url returned $code" }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_READY_TIMEOUT_MS: Long = 30_000L
        const val INITIAL_POLL_INTERVAL_MS: Long = 200L
        const val MAX_POLL_INTERVAL_MS: Long = 2_000L
        const val PROBE_TIMEOUT_MS: Int = 1_500
        const val REST_TIMEOUT_MS: Int = 5_000
    }
}
