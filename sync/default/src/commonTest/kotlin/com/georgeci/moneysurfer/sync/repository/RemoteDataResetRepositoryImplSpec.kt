package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import com.georgeci.moneysurfer.sync.db.entity.SyncMetaEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

private class RecordingPendingDao(private val log: MutableList<String>) : PendingMutationDao {
    override suspend fun insert(entity: PendingMutationEntity) = Unit
    override suspend fun pending(workspaceId: String?, limit: Int): List<PendingMutationEntity> = emptyList()
    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun deleteByIds(ids: List<String>) = Unit
    override suspend fun markFailed(id: String, error: String) = Unit
    override fun pendingCount(): Flow<Int> = MutableStateFlow(0)
    override suspend fun deleteAll() { log += "pending.deleteAll" }
}

private class RecordingMetaDao(private val log: MutableList<String>) : SyncMetaDao {
    override suspend fun get(workspaceId: String, collection: String): SyncMetaEntity? = null
    override suspend fun upsert(entity: SyncMetaEntity) = Unit
    override fun observeByWorkspace(workspaceId: String): Flow<List<SyncMetaEntity>> =
        MutableStateFlow(emptyList())
    override suspend fun deleteByWorkspace(workspaceId: String) = Unit
    override suspend fun deleteAll() { log += "meta.deleteAll" }
}

class RemoteDataResetRepositoryImplSpec : StringSpec({

    "clearAll wipes outbox before sync_meta" {
        runTest {
            val log = mutableListOf<String>()
            val repo = RemoteDataResetRepositoryImpl(
                pendingMutationDao = RecordingPendingDao(log),
                syncMetaDao = RecordingMetaDao(log),
            )

            repo.clearAll()

            log shouldContainExactly listOf("pending.deleteAll", "meta.deleteAll")
        }
    }

    "clearAll is idempotent" {
        runTest {
            val log = mutableListOf<String>()
            val repo = RemoteDataResetRepositoryImpl(
                pendingMutationDao = RecordingPendingDao(log),
                syncMetaDao = RecordingMetaDao(log),
            )

            repo.clearAll()
            repo.clearAll()

            log.size shouldBe 4
        }
    }
})
