package com.georgeci.moneysurfer.sync.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class SyncErrorSpec : StringSpec({

    "Cancelled is not retryable" {
        (SyncError.Cancelled as SyncError).isRetryable.shouldBeFalse()
    }

    "NetworkUnavailable is retryable" {
        (SyncError.NetworkUnavailable as SyncError).isRetryable.shouldBeTrue()
    }

    "AuthRequired is not retryable" {
        (SyncError.AuthRequired as SyncError).isRetryable.shouldBeFalse()
    }

    "PermissionDenied is not retryable" {
        (SyncError.PermissionDenied as SyncError).isRetryable.shouldBeFalse()
    }

    "UnsupportedAppVersion preserves message and is not retryable" {
        val error = SyncError.UnsupportedAppVersion("Please update")
        error.message shouldBe "Please update"
        error.isRetryable.shouldBeFalse()
    }

    "StorageError preserves cause and is not retryable" {
        val cause = RuntimeException("disk full")
        val error = SyncError.StorageError(cause)
        error.cause shouldBe cause
        error.isRetryable.shouldBeFalse()
    }

    "Unknown preserves cause and is retryable" {
        val cause = RuntimeException("boom")
        val error = SyncError.Unknown(cause)
        error.cause shouldBe cause
        error.isRetryable.shouldBeTrue()
    }
})
