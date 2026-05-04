package com.georgeci.moneysurfer.sync.repository

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Instant

private data class Sample(val payload: String, val updatedAt: Long)

private fun metadataFor(
    localUpdatedAt: Long?,
    remoteUpdatedAt: Long,
) = ConflictMetadata(
    entityType = "TRANSACTION",
    entityId = "txn-1",
    localUpdatedAt = localUpdatedAt?.let(Instant::fromEpochMilliseconds),
    remoteUpdatedAt = Instant.fromEpochMilliseconds(remoteUpdatedAt),
)

class LwwConflictResolverSpec : StringSpec({

    "missing local row → take remote" {
        val resolver = LwwConflictResolver()
        val resolution = resolver.resolve(
            local = null,
            remote = Sample("remote", 100),
            metadata = metadataFor(localUpdatedAt = null, remoteUpdatedAt = 100),
        )
        val taken = resolution.shouldBeInstanceOf<ConflictResolution.TakeRemote<Sample>>()
        taken.value.payload shouldBe "remote"
    }

    "remote newer than local → take remote" {
        val resolver = LwwConflictResolver()
        val resolution = resolver.resolve(
            local = Sample("local", 50),
            remote = Sample("remote", 100),
            metadata = metadataFor(localUpdatedAt = 50, remoteUpdatedAt = 100),
        )
        resolution.shouldBeInstanceOf<ConflictResolution.TakeRemote<Sample>>()
    }

    "local newer than remote → take local" {
        val resolver = LwwConflictResolver()
        val resolution = resolver.resolve(
            local = Sample("local", 200),
            remote = Sample("remote", 100),
            metadata = metadataFor(localUpdatedAt = 200, remoteUpdatedAt = 100),
        )
        val taken = resolution.shouldBeInstanceOf<ConflictResolution.TakeLocal<Sample>>()
        taken.value.payload shouldBe "local"
    }

    "equal timestamps → take local (stable, no-op re-pull)" {
        val resolver = LwwConflictResolver()
        val resolution = resolver.resolve(
            local = Sample("local", 100),
            remote = Sample("remote", 100),
            metadata = metadataFor(localUpdatedAt = 100, remoteUpdatedAt = 100),
        )
        resolution.shouldBeInstanceOf<ConflictResolution.TakeLocal<Sample>>()
    }

    "missing localUpdatedAt → remote wins (treated as DISTANT_PAST)" {
        val resolver = LwwConflictResolver()
        val resolution = resolver.resolve(
            local = Sample("local", 0),
            remote = Sample("remote", 1),
            metadata = metadataFor(localUpdatedAt = null, remoteUpdatedAt = 1),
        )
        resolution.shouldBeInstanceOf<ConflictResolution.TakeRemote<Sample>>()
    }

    "deterministic — same input gives same output" {
        val resolver = LwwConflictResolver()
        val args = Triple(
            Sample("local", 50),
            Sample("remote", 100),
            metadataFor(localUpdatedAt = 50, remoteUpdatedAt = 100),
        )
        val first = resolver.resolve(args.first, args.second, args.third)
        val second = resolver.resolve(args.first, args.second, args.third)
        first shouldBe second
    }
})
