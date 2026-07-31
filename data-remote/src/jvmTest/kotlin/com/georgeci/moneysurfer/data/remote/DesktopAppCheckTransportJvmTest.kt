package com.georgeci.moneysurfer.data.remote

import com.google.android.gms.tasks.Tasks
import com.sun.net.httpserver.HttpServer
import dev.gitlive.firebase.FirebaseOptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

private const val HTTP_OK = 200
private const val HTTP_FORBIDDEN = 403
private const val AWAIT_SECONDS = 20L

/**
 * The transport under the App Check debug exchange, exercised over a real socket rather than
 * through the injected seam the other specs use — the parts that only exist in the real path are
 * exactly the ones a developer meets first: a debug secret that was never registered comes back
 * as a 403 whose *body* carries Google's reason, and without surfacing that body the failure is
 * indistinguishable from the network being down.
 */
class DesktopAppCheckTransportJvmTest : StringSpec({

    // `Tasks.call` dispatches on Dispatchers.Main. The desktop app gets one from
    // kotlinx-coroutines-swing; a bare test JVM has none, so it is installed here.
    beforeSpec { Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined) }
    afterSpec { Dispatchers.resetMain() }

    fun serverOn(path: String, status: Int, response: String, seen: (String) -> Unit = {}): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            seen(exchange.requestBody.bufferedReader().use { it.readText() })
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    "the request body reaches the server and the response comes back" {
        var received = ""
        val server = serverOn("/exchange", HTTP_OK, """{"token":"attested","ttl":"3600s"}""") {
            received = it
        }
        try {
            val body = postJson(
                "http://127.0.0.1:${server.address.port}/exchange",
                """{"debugToken":"secret-123"}""",
            )

            received shouldContain "secret-123"
            body shouldContain "attested"
        } finally {
            server.stop(0)
        }
    }

    // A 403 here almost always means the debug secret is not registered on the project. Google
    // says so in the response body, which is only reachable through errorStream.
    "a rejected exchange surfaces the status and Google's explanation" {
        val server = serverOn(
            "/exchange",
            HTTP_FORBIDDEN,
            """{"error":{"message":"App attestation failed."}}""",
        )
        try {
            val failure = shouldThrow<HttpRequestException> {
                postJson("http://127.0.0.1:${server.address.port}/exchange", "{}")
            }

            failure.message.orEmpty() shouldContain "403"
            failure.message.orEmpty() shouldContain "App attestation failed."
        } finally {
            server.stop(0)
        }
    }

    "a transport failure is reported as such rather than escaping as an IOException" {
        // A port nothing is listening on: bind one, release it, then use the number.
        val deadPort = ServerSocket(0).use { it.localPort }

        shouldThrow<HttpRequestException> {
            postJson("http://127.0.0.1:$deadPort/exchange", "{}")
        }
    }

    "a malformed URL fails before any connection is attempted" {
        shouldThrow<HttpRequestException> { postJson("not a url", "{}") }
    }

    "the token travels out through the Task the SDK actually calls" {
        val server = serverOn("/exchange", HTTP_OK, """{"token":"attested","ttl":"3600s"}""")
        try {
            val provider = DesktopAppCheckProvider(
                options = FirebaseOptions(
                    applicationId = "1:123456789012:android:abcdef",
                    apiKey = "AIza-test-key",
                    projectId = "moneysurfer-dev",
                ),
                debugToken = "secret-123",
                exchange = { _, body ->
                    postJson("http://127.0.0.1:${server.address.port}/exchange", body)
                },
            )

            // getToken() is what Firebase invokes; the spec above only covers the call it wraps.
            Tasks.await(provider.getToken(), AWAIT_SECONDS, TimeUnit.SECONDS).token shouldBe "attested"
        } finally {
            server.stop(0)
        }
    }
})
