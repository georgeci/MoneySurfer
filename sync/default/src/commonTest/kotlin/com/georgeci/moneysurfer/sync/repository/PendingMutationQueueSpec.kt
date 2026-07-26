package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.sync.api.SyncScope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PendingMutationQueueSpec : StringSpec({

    "DEFAULT_BATCH_LIMIT is 100" {
        PendingMutationQueue.DEFAULT_BATCH_LIMIT shouldBe 100
    }

    "pending() default limit forwards DEFAULT_BATCH_LIMIT" {
        var captured: Int = -1
        val queue = object : PendingMutationQueue {
            override suspend fun enqueue(mutation: PendingMutation) = Unit
            override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> {
                captured = limit
                return emptyList()
            }
            override suspend fun markInFlight(ids: List<String>) = Unit
            override suspend fun markCompleted(ids: List<String>) = Unit
            override suspend fun markFailed(id: String, error: String) = Unit
            override val pendingCount: Flow<Int> = flowOf(0)
            override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = flowOf(emptyList())
        }
        queue.pending(SyncScope.AllUserData)
        captured shouldBe PendingMutationQueue.DEFAULT_BATCH_LIMIT
    }
})
