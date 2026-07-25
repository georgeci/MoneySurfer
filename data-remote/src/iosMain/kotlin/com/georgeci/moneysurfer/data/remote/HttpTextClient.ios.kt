package com.georgeci.moneysurfer.data.remote

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val HTTP_OK_RANGE = 200L..299L

/**
 * `NSURLSession` on the shared session — the request carries no auth, no cookies and no custom
 * headers, so the shared session's defaults (including its own request timeout) are exactly right
 * and there is no configuration to get wrong.
 */
@OptIn(BetaInteropApi::class)
internal actual suspend fun httpGetText(url: String): String = suspendCancellableCoroutine { continuation ->
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl == null) {
        continuation.resumeWithException(HttpRequestException("Malformed URL: $url"))
        return@suspendCancellableCoroutine
    }
    val task = NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, response, error ->
        val status = (response as? NSHTTPURLResponse)?.statusCode
        val body = data?.let {
            platform.Foundation.NSString.create(data = it, encoding = NSUTF8StringEncoding) as String?
        }
        when {
            error != null ->
                continuation.resumeWithException(HttpRequestException("GET $url failed: ${error.localizedDescription}"))
            status == null || status !in HTTP_OK_RANGE ->
                continuation.resumeWithException(HttpRequestException("HTTP $status from $url"))
            body == null ->
                continuation.resumeWithException(HttpRequestException("Undecodable body from $url"))
            else -> continuation.resume(body)
        }
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}
