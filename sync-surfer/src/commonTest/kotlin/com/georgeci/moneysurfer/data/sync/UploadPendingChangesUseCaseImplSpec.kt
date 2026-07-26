package com.georgeci.moneysurfer.data.sync

import arrow.core.Either
import com.georgeci.moneysurfer.domain.sync.UploadProgress
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCancelledException
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

class UploadPendingChangesUseCaseImplSpec : StringSpec({

    "empty outbox returns zero uploads without touching in-flight state" {
        val queue = FakeQueue()
        val useCase = uploadUseCase(queue, plugins = listOf(RecordingPlugin("TRANSACTION")))

        val result = useCase(SyncScope.UploadOnly, {}, SimpleCancelToken())

        result shouldBe Either.Right(UploadSummary(uploadedCount = 0))
        queue.inFlightCalls.shouldBeEmpty()
        queue.completedCalls.shouldBeEmpty()
    }

    "reads the outbox with the default batch limit" {
        val queue = FakeQueue()
        val useCase = uploadUseCase(queue, plugins = emptyList())

        useCase(SyncScope.AllUserData, {}, SimpleCancelToken())

        queue.pendingRequests shouldContainExactly listOf(
            SyncScope.AllUserData to PendingMutationQueue.DEFAULT_BATCH_LIMIT,
        )
    }

    "dispatches each mutation to the plugin matching its entityType" {
        val transactionPlugin = RecordingPlugin("TRANSACTION")
        val accountPlugin = RecordingPlugin("ACCOUNT")
        val queue = FakeQueue(
            aMutation(id = "m-1", entityType = "TRANSACTION"),
            aMutation(id = "m-2", entityType = "ACCOUNT"),
            aMutation(id = "m-3", entityType = "TRANSACTION"),
        )
        val useCase = uploadUseCase(queue, plugins = listOf(transactionPlugin, accountPlugin))

        val result = useCase(SyncScope.UploadOnly, {}, SimpleCancelToken())

        result shouldBe Either.Right(UploadSummary(uploadedCount = 3))
        transactionPlugin.pushed.map { it.id } shouldContainExactly listOf("m-1", "m-3")
        accountPlugin.pushed.map { it.id } shouldContainExactly listOf("m-2")
        queue.inFlightCalls shouldContainExactly listOf(listOf("m-1", "m-2", "m-3"))
        queue.completedCalls shouldContainExactly listOf(listOf("m-1", "m-2", "m-3"))
        queue.failedCalls.shouldBeEmpty()
    }

    "mutation without a matching plugin is skipped but still completed" {
        val plugin = RecordingPlugin("TRANSACTION")
        val queue = FakeQueue(
            aMutation(id = "m-1", entityType = "UNKNOWN_TYPE"),
            aMutation(id = "m-2", entityType = "TRANSACTION"),
        )
        val useCase = uploadUseCase(queue, plugins = listOf(plugin))

        val result = useCase(SyncScope.UploadOnly, {}, SimpleCancelToken())

        // The unknown row must not wedge the queue forever — it completes and is dropped.
        result shouldBe Either.Right(UploadSummary(uploadedCount = 2))
        plugin.pushed.map { it.id } shouldContainExactly listOf("m-2")
        queue.completedCalls shouldContainExactly listOf(listOf("m-1", "m-2"))
    }

    "reports progress with entityType and running position" {
        val queue = FakeQueue(
            aMutation(id = "m-1", entityType = "TRANSACTION"),
            aMutation(id = "m-2", entityType = "TRANSACTION"),
        )
        val useCase = uploadUseCase(queue, plugins = listOf(RecordingPlugin("TRANSACTION")))
        val progress = mutableListOf<UploadProgress>()

        useCase(SyncScope.UploadOnly, { progress += it }, SimpleCancelToken())

        progress shouldContainExactly listOf(
            UploadProgress(entityType = "TRANSACTION", current = 1, total = 2),
            UploadProgress(entityType = "TRANSACTION", current = 2, total = 2),
        )
    }

    "push failure completes the uploaded prefix and fails the remainder" {
        val plugin = RecordingPlugin("TRANSACTION", failOnId = "m-2", failure = RuntimeException("boom"))
        val queue = FakeQueue(
            aMutation(id = "m-1", entityType = "TRANSACTION"),
            aMutation(id = "m-2", entityType = "TRANSACTION"),
            aMutation(id = "m-3", entityType = "TRANSACTION"),
        )
        val useCase = uploadUseCase(queue, plugins = listOf(plugin))

        val result = useCase(SyncScope.UploadOnly, {}, SimpleCancelToken())

        val left = result.shouldBeInstanceOf<Either.Left<SyncError>>()
        left.value.shouldBeInstanceOf<SyncError.Unknown>()
        // m-1 landed remotely before the failure — it must never be re-pushed.
        queue.completedCalls shouldContainExactly listOf(listOf("m-1"))
        // m-2 (the failed one) and m-3 (never attempted) both return to PENDING.
        queue.failedCalls.map { it.first } shouldContainExactly listOf("m-2", "m-3")
        queue.failedCalls.map { it.second }.toSet() shouldBe setOf("boom")
    }

    "cancellation mid-batch completes the pushed prefix and re-queues the rest as cancelled" {
        val token = SimpleCancelToken()
        // Cancels the token while pushing m-1; the check before m-2 then throws.
        val plugin = RecordingPlugin("TRANSACTION", onPush = { token.cancel() })
        val queue = FakeQueue(
            aMutation(id = "m-1", entityType = "TRANSACTION"),
            aMutation(id = "m-2", entityType = "TRANSACTION"),
        )
        val useCase = uploadUseCase(queue, plugins = listOf(plugin))

        shouldThrow<SyncCancelledException> {
            useCase(SyncScope.UploadOnly, {}, token)
        }

        plugin.pushed.map { it.id } shouldContainExactly listOf("m-1")
        queue.completedCalls shouldContainExactly listOf(listOf("m-1"))
        queue.failedCalls shouldContainExactly listOf("m-2" to "cancelled")
    }

    "failure on the first mutation fails the whole batch and completes nothing" {
        val plugin = RecordingPlugin("TRANSACTION", failOnId = "m-1", failure = RuntimeException("dead"))
        val queue = FakeQueue(
            aMutation(id = "m-1", entityType = "TRANSACTION"),
            aMutation(id = "m-2", entityType = "TRANSACTION"),
        )
        val useCase = uploadUseCase(queue, plugins = listOf(plugin))

        val result = useCase(SyncScope.UploadOnly, {}, SimpleCancelToken())

        result.shouldBeInstanceOf<Either.Left<SyncError>>()
        queue.completedCalls shouldHaveSize 1
        queue.completedCalls.single().shouldBeEmpty()
        queue.failedCalls.map { it.first } shouldContainExactly listOf("m-1", "m-2")
    }
})

private fun uploadUseCase(
    queue: PendingMutationQueue,
    plugins: List<SyncEntityPlugin>,
): UploadPendingChangesUseCaseImpl = UploadPendingChangesUseCaseImpl(
    outbox = queue,
    authInfo = FakeAuthInfo,
    plugins = plugins,
)

private object FakeAuthInfo : CurrentAuthInfo {
    override fun diagnostics(): String = "test-auth"
}

private fun aMutation(
    id: String,
    entityType: String,
    entityId: String = "entity-$id",
): PendingMutation = PendingMutation(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = MutationOperation.INSERT,
    scopeKey = "ws-1",
    createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    attempts = 0,
    lastError = null,
)

private class FakeQueue(vararg mutations: PendingMutation) : PendingMutationQueue {
    private val rows = mutations.toMutableList()

    val pendingRequests = mutableListOf<Pair<SyncScope, Int>>()
    val inFlightCalls = mutableListOf<List<String>>()
    val completedCalls = mutableListOf<List<String>>()
    val failedCalls = mutableListOf<Pair<String, String>>()

    override suspend fun enqueue(mutation: PendingMutation) {
        rows += mutation
    }

    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> {
        pendingRequests += scope to limit
        return rows.take(limit)
    }

    override suspend fun markInFlight(ids: List<String>) {
        inFlightCalls += ids
    }

    override suspend fun markCompleted(ids: List<String>) {
        completedCalls += ids
        rows.removeAll { it.id in ids }
    }

    override suspend fun markFailed(id: String, error: String) {
        failedCalls += id to error
    }

    override val pendingCount: Flow<Int> = flowOf(rows.size)
    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = flowOf(emptyList())
}

private class RecordingPlugin(
    override val entityType: String,
    private val failOnId: String? = null,
    private val failure: Throwable = RuntimeException("push failed"),
    private val onPush: (PendingMutation) -> Unit = {},
) : SyncEntityPlugin {
    override val firestoreCollectionName: String? = null

    val pushed = mutableListOf<PendingMutation>()

    override suspend fun push(mutation: PendingMutation) {
        if (mutation.id == failOnId) throw failure
        pushed += mutation
        onPush(mutation)
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult =
        error("applyDoc is not part of the upload path")
}
