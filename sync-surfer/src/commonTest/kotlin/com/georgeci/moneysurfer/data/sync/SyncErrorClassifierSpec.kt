package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.api.SyncError
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

class SyncErrorClassifierSpec : StringSpec({

    "CancellationException maps to Cancelled" {
        CancellationException("user cancelled").toSyncError() shouldBe SyncError.Cancelled
    }

    "SerializationException maps to Unknown" {
        val cause = SerializationException("bad json")
        val mapped = cause.toSyncError().shouldBeInstanceOf<SyncError.Unknown>()
        mapped.cause shouldBe cause
    }

    "Generic exception maps to Unknown" {
        val cause = IllegalStateException("oops")
        val mapped = cause.toSyncError().shouldBeInstanceOf<SyncError.Unknown>()
        mapped.cause shouldBe cause
    }
})
