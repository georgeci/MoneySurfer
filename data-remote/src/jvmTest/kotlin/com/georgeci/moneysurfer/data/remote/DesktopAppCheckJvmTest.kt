package com.georgeci.moneysurfer.data.remote

import dev.gitlive.firebase.FirebaseOptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val TTL_SECONDS = 3600L
private const val MILLIS_PER_SECOND = 1_000L

/**
 * The desktop host has no attestation provider, so it trades a registered debug secret for an
 * App Check token over REST. The failure modes are all silent-until-enforced — a wrong project
 * number or an unparsed TTL only shows up once enforcement is switched on — so the request the
 * provider builds and the response it accepts are pinned here.
 */
class DesktopAppCheckJvmTest : StringSpec({

    val options = FirebaseOptions(
        applicationId = "1:123456789012:android:abcdef",
        apiKey = "AIza-test-key",
        projectId = "moneysurfer-dev",
    )

    "the project number comes from the app id, not a separate variable" {
        projectNumberOf("1:123456789012:android:abcdef") shouldBe "123456789012"
    }

    "an app id that is not in Firebase's form fails loudly" {
        val failure = shouldThrow<IllegalStateException> { projectNumberOf("not-an-app-id") }

        failure.message.orEmpty() shouldContain "MS_FIREBASE_APP_ID"
    }

    "the protobuf duration Google returns is read as seconds" {
        secondsOf("3600s") shouldBe TTL_SECONDS
    }

    // An unparsed TTL previously yielded a token that never looked expired, so the SDK would
    // keep presenting a stale one. Treating it as zero forces a refresh instead.
    "an unreadable TTL expires immediately rather than never" {
        secondsOf("garbage") shouldBe 0L
    }

    "the exchange targets the project-number path and carries the debug secret" {
        var seenUrl = ""
        var seenBody = ""
        val provider = DesktopAppCheckProvider(
            options = options,
            debugToken = "secret-123",
            exchange = { url, body ->
                seenUrl = url
                seenBody = body
                """{"token":"attested","ttl":"3600s"}"""
            },
        )

        provider.exchangeDebugToken()

        seenUrl shouldContain "/projects/123456789012/apps/1:123456789012:android:abcdef"
        seenUrl shouldContain ":exchangeDebugToken?key=AIza-test-key"
        seenBody shouldContain "secret-123"
    }

    "a successful exchange yields the token with its expiry applied" {
        val before = System.currentTimeMillis()
        val provider = DesktopAppCheckProvider(
            options = options,
            debugToken = "secret-123",
            exchange = { _, _ -> """{"token":"attested","ttl":"3600s"}""" },
        )

        val token = provider.exchangeDebugToken()

        token.token shouldBe "attested"
        token.expireTimeMillis shouldBeGreaterThan before + (TTL_SECONDS - 1) * MILLIS_PER_SECOND
    }

    // Google adds response fields over time; an unknown one must not take the host down.
    "unknown response fields are ignored" {
        val provider = DesktopAppCheckProvider(
            options = options,
            debugToken = "secret-123",
            exchange = { _, _ -> """{"token":"attested","ttl":"60s","somethingNew":true}""" },
        )

        provider.exchangeDebugToken().token shouldBe "attested"
    }

    "without a debug token nothing is installed, so the host keeps working as before" {
        // A throwing exchange proves no network call is attempted, and reaching
        // FirebaseAppCheck.getInstance() would fail here since no FirebaseApp exists.
        installDesktopAppCheck(
            options = options,
            env = { null },
            exchange = { _, _ -> error("must not be called") },
        )
    }

    "a blank debug token is treated as absent rather than sent" {
        installDesktopAppCheck(
            options = options,
            env = { "   " },
            exchange = { _, _ -> error("must not be called") },
        )
    }
})
