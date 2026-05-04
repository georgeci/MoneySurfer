package com.georgeci.moneysurfer.sync.repository

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class SyncMetaRepositorySpec : StringSpec({

    val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    "SyncMeta carries cursor and timestamps" {
        val meta = SyncMeta(
            scopeKey = "ws-1",
            collection = "transactions",
            lastPulledAt = now,
            lastSyncSuccessAt = now,
            lastSyncAttemptAt = now,
        )
        meta.scopeKey shouldBe "ws-1"
        meta.collection shouldBe "transactions"
        meta.lastPulledAt shouldBe now
        meta.lastSyncSuccessAt shouldBe now
        meta.lastSyncAttemptAt shouldBe now
    }

    "SyncMeta supports null timestamps for fresh workspaces" {
        val meta = SyncMeta(
            scopeKey = "ws-2",
            collection = "accounts",
            lastPulledAt = null,
            lastSyncSuccessAt = null,
            lastSyncAttemptAt = null,
        )
        meta.lastPulledAt shouldBe null
        meta.lastSyncSuccessAt shouldBe null
        meta.lastSyncAttemptAt shouldBe null
    }

    "SyncMeta copy override changes targeted field only" {
        val base = SyncMeta(
            scopeKey = "ws-3",
            collection = "categories",
            lastPulledAt = null,
            lastSyncSuccessAt = null,
            lastSyncAttemptAt = null,
        )
        val updated = base.copy(lastPulledAt = now)
        updated.lastPulledAt shouldBe now
        updated.lastSyncAttemptAt shouldBe null
        updated.scopeKey shouldBe base.scopeKey
    }
})
