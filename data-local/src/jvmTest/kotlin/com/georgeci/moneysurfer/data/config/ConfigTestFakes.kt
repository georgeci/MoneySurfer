package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.data.db.entity.ConfigEntryEntity
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory `config_entry` reproducing the column semantics of the real statements — which column
 * each write leaves alone is the whole point of them, so a fake that ignored that would make the
 * tests above it meaningless. The statements themselves are covered against real SQLite in
 * `:integration-test`.
 *
 * [failReads] models an unreadable table: both the one-shot read and the flow throw, which is what
 * the degradation path has to survive.
 */
internal class FakeConfigEntryDao(private val failReads: Boolean = false) : ConfigEntryDao {

    private val rows = MutableStateFlow<Map<String, ConfigEntryEntity>>(emptyMap())

    var lastWriteAt: Long = 0L
        private set

    override fun observeAll(): Flow<List<ConfigEntryEntity>> = rows.map { snapshot ->
        if (failReads) error("config_entry is unreadable") else snapshot.values.toList()
    }

    override suspend fun getAll(): List<ConfigEntryEntity> =
        if (failReads) error("config_entry is unreadable") else rows.value.values.toList()

    override suspend fun getByKey(key: String): ConfigEntryEntity? = rows.value[key]

    override suspend fun write(key: String, value: String, updatedAt: Long) {
        lastWriteAt = updatedAt
        val existing = rows.value[key]
        rows.value = rows.value + (
            // The real statement leaves `lastPushedAt` out of its update list.
            key to ConfigEntryEntity(key, value, updatedAt, existing?.lastPushedAt)
            )
    }

    override suspend fun applyRemote(key: String, value: String, updatedAt: Long) {
        rows.value = rows.value + (key to ConfigEntryEntity(key, value, updatedAt, updatedAt))
    }

    override suspend fun markPushed(key: String, pushedUpdatedAt: Long) {
        val row = rows.value[key] ?: return
        if (row.updatedAt != pushedUpdatedAt) return
        rows.value = rows.value + (key to row.copy(lastPushedAt = pushedUpdatedAt))
    }

    override suspend fun keysPendingPush(): List<String> = rows.value.values
        .filter { row -> row.lastPushedAt?.let { row.updatedAt > it } ?: true }
        .map { it.key }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}

/** Records what a dual-write queued: `(entityType, entityId, scopeKey)`. */
internal class RecordingOutbox : OutboxEnqueuer {
    val upserts = mutableListOf<Triple<String, String, String?>>()

    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) {
        upserts += Triple(entityType, entityId, scopeKey)
    }

    override suspend fun enqueueDelete(entityType: String, entityId: String, scopeKey: String?) = Unit

    override suspend fun isEnabled(): Boolean = true
}
