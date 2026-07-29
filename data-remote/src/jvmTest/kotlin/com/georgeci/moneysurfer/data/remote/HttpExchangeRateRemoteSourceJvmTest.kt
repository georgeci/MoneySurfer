package com.georgeci.moneysurfer.data.remote

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.time.Instant

private val USD = CurrencyCode("USD")

private const val SUCCESS_BODY = """
    {
      "result": "success",
      "provider": "https://www.exchangerate-api.com",
      "base_code": "USD",
      "time_last_update_unix": 1735689600,
      "time_next_update_utc": "Fri, 03 Jan 2025 00:02:31 +0000",
      "rates": { "USD": 1, "EUR": 0.95 }
    }
"""

/** A stand-in provider on a loopback port — the real `java.net` path, without the real internet. */
private class StubProvider(
    private val status: Int = 200,
    private val body: String = SUCCESS_BODY,
) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> respond(exchange) }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/v6/latest"

    private fun respond(exchange: HttpExchange) {
        val bytes = body.encodeToByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    fun stop() = server.stop(0)
}

private suspend fun fetchFrom(provider: StubProvider) =
    HttpExchangeRateRemoteSource(ExchangeRateApiConfig(baseUrl = provider.baseUrl))
        .fetchLatest(USD)

/**
 * The only non-Firebase network call the app makes, over the real `HttpURLConnection` actual.
 *
 * Every failure mode has the same answer — `null`, meaning "keep the cached table" — so the risk
 * this pins down is the opposite one: a transport error escaping as an exception would surface as
 * an app-level failure on a screen whose contract is that stale rates are fine.
 */
class HttpExchangeRateRemoteSourceJvmTest : StringSpec({

    "a well-formed response becomes the domain table" {
        runTest {
            val provider = StubProvider()
            try {
                val table = fetchFrom(provider)!!

                table.baseCurrency shouldBe USD
                table.rates shouldBe mapOf(CurrencyCode("USD") to 1.0, CurrencyCode("EUR") to 0.95)
                table.asOf shouldBe Instant.fromEpochSeconds(1_735_689_600L)
            } finally {
                provider.stop()
            }
        }
    }

    "a 5xx from the provider reads as no answer" {
        runTest {
            val provider = StubProvider(status = 503, body = "upstream is down")
            try {
                fetchFrom(provider).shouldBeNull()
            } finally {
                provider.stop()
            }
        }
    }

    "a body that is not the payload we expect reads as no answer" {
        runTest {
            val provider = StubProvider(body = """{"rates": "not an object"}""")
            try {
                fetchFrom(provider).shouldBeNull()
            } finally {
                provider.stop()
            }
        }
    }

    "a payload the provider marked as failed reads as no answer" {
        runTest {
            val provider = StubProvider(
                body = """{"result":"error","error-type":"unsupported-code"}""",
            )
            try {
                fetchFrom(provider).shouldBeNull()
            } finally {
                provider.stop()
            }
        }
    }

    // Offline is the expected state on a phone, not an error the caller can act on.
    "an unreachable host reads as no answer rather than throwing" {
        runTest {
            val source = HttpExchangeRateRemoteSource(
                ExchangeRateApiConfig(baseUrl = "http://127.0.0.1:1/v6/latest"),
            )

            source.fetchLatest(USD).shouldBeNull()
        }
    }

    "a malformed base url reads as no answer rather than throwing" {
        runTest {
            val source = HttpExchangeRateRemoteSource(ExchangeRateApiConfig(baseUrl = "not a url"))

            source.fetchLatest(USD).shouldBeNull()
        }
    }
})
