package com.georgeci.moneysurfer.sync.repository

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant

class PendingMutationSpec : StringSpec({

    val template = PendingMutation(
        id = "pm-1",
        entityType = "TRANSACTION",
        entityId = "tx-42",
        operation = MutationOperation.INSERT,
        scopeKey = "ws-1",
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        attempts = 0,
        lastError = null,
    )

    "fields are exposed" {
        template.id shouldBe "pm-1"
        template.entityType shouldBe "TRANSACTION"
        template.entityId shouldBe "tx-42"
        template.operation shouldBe MutationOperation.INSERT
        template.scopeKey shouldBe "ws-1"
        template.attempts shouldBe 0
        template.lastError shouldBe null
    }

    "copy with override" {
        val updated = template.copy(attempts = 3, lastError = "boom")
        updated.attempts shouldBe 3
        updated.lastError shouldBe "boom"
        updated.id shouldBe template.id
        updated shouldNotBe template
    }

    "equals and hashCode treat identical fields as equal" {
        val twin = template.copy()
        twin shouldBe template
        twin.hashCode() shouldBe template.hashCode()
    }

    "toString contains the id and entityType" {
        val rendered = template.toString()
        rendered.contains("pm-1") shouldBe true
        rendered.contains("TRANSACTION") shouldBe true
    }

    "MutationOperation enumerates INSERT, UPDATE, DELETE" {
        MutationOperation.entries.toSet() shouldBe setOf(
            MutationOperation.INSERT,
            MutationOperation.UPDATE,
            MutationOperation.DELETE,
        )
        MutationOperation.valueOf("INSERT") shouldBe MutationOperation.INSERT
    }
})
