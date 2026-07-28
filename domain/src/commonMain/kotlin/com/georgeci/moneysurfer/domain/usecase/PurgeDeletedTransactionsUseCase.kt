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
 * **Why thirty days.** The window is not about the Undo — that is a Snackbar and expires in
 * seconds. It is about the other devices: a peer that has been offline for a while pulls the
 * tombstone by `updatedAt`, and purging locally before it syncs would leave this device with no
 * record either way. Thirty days is comfortably past the sync cadence and short enough that a
 * deleted row does not linger in a backup for a year.
 *
 * **What it does not do.** The remote doc keeps its `deletedAt` — that tombstone is how peers
 * learn about the delete at all, and it is not this device's to collect. Remote tombstone GC
 * remains an open gap (docs/architecture/sync-gaps.md).
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
