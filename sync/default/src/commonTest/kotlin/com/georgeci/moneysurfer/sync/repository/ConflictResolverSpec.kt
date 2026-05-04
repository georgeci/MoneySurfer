package com.georgeci.moneysurfer.sync.repository

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class ConflictResolverSpec : StringSpec({

    val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    "ConflictMetadata exposes its fields" {
        val meta = ConflictMetadata(
            entityType = "ACCOUNT",
            entityId = "acc-1",
            localUpdatedAt = null,
            remoteUpdatedAt = now,
        )
        meta.entityType shouldBe "ACCOUNT"
        meta.entityId shouldBe "acc-1"
        meta.localUpdatedAt shouldBe null
        meta.remoteUpdatedAt shouldBe now
    }

    "ConflictResolution.TakeRemote carries the remote value" {
        val res = ConflictResolution.TakeRemote(value = "remote")
        res.value shouldBe "remote"
    }

    "ConflictResolution.TakeLocal carries the local value" {
        val res = ConflictResolution.TakeLocal(value = "local")
        res.value shouldBe "local"
    }

    "ConflictResolution.Merged carries the merged value" {
        val res = ConflictResolution.Merged(value = "merged")
        res.value shouldBe "merged"
    }

    "ConflictResolution.Skip is a singleton" {
        (ConflictResolution.Skip as ConflictResolution<Nothing>) shouldBe ConflictResolution.Skip
    }

    "ConflictResolver implementation can be invoked" {
        val resolver = object : ConflictResolver {
            override fun <T : Any> resolve(
                local: T?,
                remote: T,
                metadata: ConflictMetadata,
            ): ConflictResolution<T> = ConflictResolution.TakeRemote(remote)
        }
        val resolution = resolver.resolve(
            local = "old",
            remote = "new",
            metadata = ConflictMetadata("CATEGORY", "c-1", null, now),
        )
        (resolution as ConflictResolution.TakeRemote).value shouldBe "new"
    }
})
