package com.georgeci.moneysurfer.sync.internal

import com.georgeci.moneysurfer.sync.api.SyncError
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

class SyncErrorClassifierSpec : StringSpec({

    "CancellationException maps to SyncError.Cancelled" {
        val mapped = (CancellationException("stop") as Throwable).toSyncError()
        mapped shouldBe SyncError.Cancelled
    }

    "SerializationException maps to Unknown carrying the cause" {
        val cause = SerializationException("bad payload")
        val mapped = (cause as Throwable).toSyncError()
        val unknown = mapped.shouldBeInstanceOf<SyncError.Unknown>()
        unknown.cause shouldBe cause
    }

    "arbitrary throwable maps to Unknown carrying the cause" {
        val cause = IllegalStateException("oops")
        val mapped = (cause as Throwable).toSyncError()
        val unknown = mapped.shouldBeInstanceOf<SyncError.Unknown>()
        unknown.cause shouldBe cause
    }

    "Unknown.isRetryable is true (sync should be re-attempted on next cycle)" {
        val mapped = (RuntimeException("boom") as Throwable).toSyncError()
        mapped.isRetryable shouldBe true
    }

    "Cancelled.isRetryable is false" {
        val mapped = (CancellationException("stop") as Throwable).toSyncError()
        mapped.isRetryable shouldBe false
    }
})
