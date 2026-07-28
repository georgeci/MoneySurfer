package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.repositories.TransactionRetentionRepository
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The retention half of soft delete (issue #346): tombstoned transactions are kept for
 * [RETENTION] and then dropped for good.
 *
 * **Who runs it.** The app itself, once per launch, from `AppLaunchViewModel` — the same place
 * the first-run seed repairs a half-finished install. Deliberately not a background worker: the
 * work is a single indexed DELETE over rows nobody is reading, there is nothing to schedule
 * around, and a worker would need scheduling on two platforms to do what one call at startup
 * already does. An app that is never launched keeps its tombstones, which costs a few rows and
 * loses nothing.
 *
 * **Why thirty days.** Everything that reads a tombstone is a local, short-lived interaction: the
 * Undo Snackbar (seconds), an edit that was already open when the row was deleted
 * ([UpdateTransactionUseCase]), and a CSV import, which must find the tombstone rather than insert
 * over a surviving id. Thirty days is far past all three and short enough that deleted rows do not
 * accumulate. Nothing here is waiting on another device — see below.
 *
 * **The window is measured from the delete's own timestamp**, which for a tombstone that arrived by
 * sync is the clock of the device that made the delete, not the moment this one heard about it. A
 * delete pushed by a peer that had been offline for longer than [RETENTION] therefore lands here
 * already expired and is collected on the next launch. That is deliberate rather than an oversight:
 * none of the three readers above outlives the trip, there is no UI for undoing a delete made on
 * another device, and a CSV import that finds no tombstone simply inserts the row instead — the
 * same end state.
 *
 * **What it does not do.** The remote doc keeps its `deletedAt`. Purging here says nothing about
 * any peer's copy — the tombstone stays on the server for them to pull, and collecting *that* is a
 * separate open gap (docs/architecture/sync-gaps.md).
 */
@Single
class PurgeDeletedTransactionsUseCase(
    private val retention: TransactionRetentionRepository,
    private val clock: ClockUseCase,
) {

    /** Returns how many tombstones were dropped. */
    suspend operator fun invoke(): Int = retention.purgeDeletedBefore(clock.now() - RETENTION)

    companion object {
        /** How long a deleted transaction stays recoverable before it is gone for good. */
        val RETENTION: Duration = 30.days
    }
}
