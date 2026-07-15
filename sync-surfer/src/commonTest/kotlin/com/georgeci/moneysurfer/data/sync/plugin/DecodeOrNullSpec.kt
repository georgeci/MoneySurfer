package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Poison-document tolerance (issue #156). `decodeOrNull` must swallow a malformed payload
 * (returning null so the plugin skips it and the pull cursor still advances) but never swallow
 * cancellation.
 */
class DecodeOrNullSpec : StringSpec({

    "returns the decoded value when the document is well-formed" {
        val expected = AccountDoc(name = "Checking", balance = 42)
        val doc = FakeRemoteDocument { expected }

        doc.decodeOrNull(AccountDoc.serializer()) shouldBe expected
    }

    "returns null when decode throws SerializationException (wrong field type)" {
        val doc = FakeRemoteDocument { throw SerializationException("amount is a string") }

        doc.decodeOrNull(AccountDoc.serializer()).shouldBeNull()
    }

    "returns null when decode throws a ClassCastException (platform decoder mismatch)" {
        // gitlive's Firestore decoder can surface a raw cast failure rather than a
        // SerializationException, so the guard must catch broadly — not just one type.
        val doc = FakeRemoteDocument { throw ClassCastException("String cannot be cast to Long") }

        doc.decodeOrNull(AccountDoc.serializer()).shouldBeNull()
    }

    "rethrows CancellationException instead of swallowing it" {
        val doc = FakeRemoteDocument { throw CancellationException("pull cancelled") }

        shouldThrow<CancellationException> {
            doc.decodeOrNull(AccountDoc.serializer())
        }
    }
})

private class FakeRemoteDocument(
    override val id: String = "doc-1",
    private val onDecode: () -> Any?,
) : RemoteDocument {
    @Suppress("UNCHECKED_CAST")
    override fun <T> decode(deserializer: DeserializationStrategy<T>): T = onDecode() as T
    override fun getLong(field: String): Long? = null
}
