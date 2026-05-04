package com.georgeci.moneysurfer.sync.internal.repository

import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import com.georgeci.moneysurfer.sync.db.entity.SyncMetaEntity
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import com.georgeci.moneysurfer.sync.repository.SyncMetaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single(binds = [SyncMetaRepository::class])
class SyncMetaRepositoryImpl(
    private val dao: SyncMetaDao,
) : SyncMetaRepository {

    override suspend fun cursor(scopeKey: String, collection: String): Instant? =
        dao.get(scopeKey, collection)
            ?.lastPulledAt
            ?.let(Instant::fromEpochMilliseconds)

    override suspend fun setCursor(scopeKey: String, collection: String, cursor: Instant) {
        val existing = dao.get(scopeKey, collection)
        dao.upsert(
            (existing ?: blank(scopeKey, collection)).copy(
                lastPulledAt = cursor.toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun markAttempt(scopeKey: String, collection: String, at: Instant) {
        val existing = dao.get(scopeKey, collection)
        dao.upsert(
            (existing ?: blank(scopeKey, collection)).copy(
                lastSyncAttemptAt = at.toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun markSuccess(scopeKey: String, collection: String, at: Instant) {
        val existing = dao.get(scopeKey, collection)
        dao.upsert(
            (existing ?: blank(scopeKey, collection)).copy(
                lastSyncSuccessAt = at.toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun clearScope(scopeKey: String) {
        dao.deleteByWorkspace(scopeKey)
    }

    override fun observe(scopeKey: String): Flow<List<SyncMeta>> =
        dao.observeByWorkspace(scopeKey).map { rows -> rows.map { it.toDomain() } }

    private fun blank(scopeKey: String, collection: String) = SyncMetaEntity(
        workspaceId = scopeKey,
        collection = collection,
        lastPulledAt = null,
        lastSyncSuccessAt = null,
        lastSyncAttemptAt = null,
    )
}

private fun SyncMetaEntity.toDomain() = SyncMeta(
    scopeKey = workspaceId,
    collection = collection,
    lastPulledAt = lastPulledAt?.let(Instant::fromEpochMilliseconds),
    lastSyncSuccessAt = lastSyncSuccessAt?.let(Instant::fromEpochMilliseconds),
    lastSyncAttemptAt = lastSyncAttemptAt?.let(Instant::fromEpochMilliseconds),
)
