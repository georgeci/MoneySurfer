package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.remote.TransactionDoc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val WORKSPACE = "ws-1"
private const val TX = "tx-1"

private fun transactionDoc(
    merchant: String = "",
    tags: List<String> = emptyList(),
    recurringRuleId: String? = null,
    splitId: String? = null,
) = TransactionDoc(
    accountId = "acc-1",
    amount = 500,
    currencyCode = "USD",
    note = "Groceries",
    merchant = merchant,
    tags = tags,
    operationAt = 1,
    operationDate = "2026-07-15",
    type = "EXPENSE",
    status = "ACTUAL",
    createdAt = 1,
    updatedAt = 2,
    recurringRuleId = recurringRuleId,
    splitId = splitId,
)

class TransactionDtoMapperSpec : StringSpec({

    "merchant, tags and recurringRuleId round-trip through the wire format" {
        val doc = transactionDoc(
            merchant = "Starbucks",
            tags = listOf("coffee", "work"),
            recurringRuleId = "rule-1",
        )

        val entity = doc.toEntity(id = TX, workspaceId = WORKSPACE)

        entity.merchant shouldBe "Starbucks"
        entity.tags shouldBe "coffee,work"
        entity.recurringRuleId shouldBe "rule-1"
        entity.toDoc().copy(clientVersionCode = 1) shouldBe doc
    }

    // A doc written by a client we do not control can carry a comma; storing it verbatim
    // would make Room read one tag back as two.
    "a remote tag containing the CSV separator is normalized, not split" {
        val entity = transactionDoc(tags = listOf("coffee,work"))
            .toEntity(id = TX, workspaceId = WORKSPACE)

        entity.tags shouldBe "coffee work"
        entity.toDoc().tags shouldBe listOf("coffee work")
    }

    "blank remote tags are dropped rather than stored as empty segments" {
        transactionDoc(tags = listOf("coffee", "", "  ", "work"))
            .toEntity(id = TX, workspaceId = WORKSPACE)
            .tags shouldBe "coffee,work"
    }

    // Docs written by the previous build have none of these fields; the DTO defaults must
    // decode them to the empty state instead of failing or producing a blank-segment CSV.
    "a doc from an older client decodes to empty merchant, no tags and no rule" {
        val entity = TransactionDoc(
            accountId = "acc-1",
            amount = 500,
            currencyCode = "USD",
            note = "Groceries",
            operationAt = 1,
            operationDate = "2026-07-15",
            type = "EXPENSE",
            status = "ACTUAL",
            createdAt = 1,
            updatedAt = 2,
        ).toEntity(id = TX, workspaceId = WORKSPACE)

        entity.merchant shouldBe ""
        entity.tags shouldBe ""
        entity.recurringRuleId shouldBe null
        entity.splitId shouldBe null
        entity.toDoc().tags shouldBe emptyList()
    }

    // A split leg is a self-contained document — which is exactly why splits replicate correctly
    // under per-entity LWW — so the only thing the wire format owes it is this one id.
    "a split leg's group id round-trips through the wire format" {
        val doc = transactionDoc(splitId = "sp-1")

        val entity = doc.toEntity(id = TX, workspaceId = WORKSPACE)

        entity.splitId shouldBe "sp-1"
        entity.toDoc().copy(clientVersionCode = 1) shouldBe doc
    }
})
