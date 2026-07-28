package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.repositories.TransactionRetentionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000L)

/**
 * The retention policy is one subtraction, and it is the whole of the guarantee: get the sign or
 * the unit wrong and the purge either never fires or takes every tombstone the moment it is
 * written — losing an Undo that was still on screen.
 */
class PurgeDeletedTransactionsUseCaseTest : StringSpec({

    "the threshold is exactly one retention window behind now" {
        val asked = mutableListOf<Instant>()
        val useCase = PurgeDeletedTransactionsUseCase(
            retention = TransactionRetentionRepository { threshold -> asked.add(threshold).let { 0 } },
            clock = ClockUseCase(FixedClock(NOW)),
        )

        useCase()

        asked shouldBe listOf(NOW - PurgeDeletedTransactionsUseCase.RETENTION)
    }

    // A window shorter than the Snackbar would collect a row the user can still tap Undo on; one
    // this long is the documented promise, so it is worth pinning rather than left to a comment.
    "the retention window is thirty days" {
        PurgeDeletedTransactionsUseCase.RETENTION shouldBe 30.days
    }

    "the number of purged rows is reported back to the caller" {
        val useCase = PurgeDeletedTransactionsUseCase(
            retention = TransactionRetentionRepository { 7 },
            clock = ClockUseCase(FixedClock(NOW)),
        )

        useCase() shouldBe 7
    }
})
