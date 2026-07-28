package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.domain.repositories.TransactionRetentionRepository
import org.koin.core.annotation.Single
import kotlin.time.Instant

/**
 * Purges expired transaction tombstones from Room (issue #346).
 *
 * No outbox write on purpose. The remote doc keeps its `deletedAt` — that tombstone is what tells
 * a peer which has not synced since the delete that the row is gone, and dropping it locally says
 * nothing about the peer's copy. Remote tombstone GC is a separate, still-open gap tracked in
 * docs/architecture/sync-gaps.md.
 */
@Single(binds = [TransactionRetentionRepository::class])
class TransactionRetentionRepositoryImpl(
    private val dao: TransactionDao,
) : TransactionRetentionRepository {

    override suspend fun purgeDeletedBefore(threshold: Instant): Int =
        dao.purgeDeletedBefore(threshold.toEpochMilliseconds())
}
