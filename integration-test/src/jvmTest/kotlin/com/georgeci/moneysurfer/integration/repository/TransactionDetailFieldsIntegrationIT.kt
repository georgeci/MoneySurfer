package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.model.TransactionTags
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * Round-trips `merchant`, `tags` and `recurringRuleId` through real Room.
 *
 * `tags` is the one that can actually break: it lives in a single CSV column, so this
 * pins down that the encoding survives a write/read cycle and that a tag carrying the
 * separator cannot come back as two.
 */
class TransactionDetailFieldsIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
        stack.accountRepository.insert(
            anAccount(id = accountId("a-1"), workspaceId = DEFAULT_WORKSPACE_ID),
        )
    }

    afterEach { harness.close() }

    suspend fun store(
        id: String,
        merchant: String = "",
        tags: List<String> = emptyList(),
        rule: RecurringRuleId? = null,
    ) {
        stack.transactionRepository.insert(
            aTransaction(
                id = transactionId(id),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = accountId("a-1"),
                categoryId = null,
                merchant = merchant,
                tags = tags,
                recurringRuleId = rule,
            ),
        )
    }

    "merchant, tags and recurringRuleId survive a write/read cycle" {
        store(
            id = "t-1",
            merchant = "Starbucks",
            tags = listOf("coffee", "work"),
            rule = RecurringRuleId("rule-1"),
        )

        val stored = stack.transactionRepository.getById(transactionId("t-1"))!!

        stored.merchant shouldBe "Starbucks"
        stored.tags shouldBe listOf("coffee", "work")
        stored.recurringRuleId shouldBe RecurringRuleId("rule-1")
    }

    "the defaults read back as empty rather than null or a blank tag" {
        store(id = "t-1")

        val stored = stack.transactionRepository.getById(transactionId("t-1"))!!

        stored.merchant shouldBe ""
        stored.tags.shouldBeEmpty()
        stored.recurringRuleId shouldBe null
    }

    // The CSV column is why TransactionTags.normalize runs on the way in; without it this
    // would read back as ["coffee", "work"].
    "a tag containing the CSV separator does not split into two on read" {
        store(id = "t-1", tags = listOf("coffee,work"))

        stack.transactionRepository.getById(transactionId("t-1"))!!.tags shouldBe
            listOf("coffee work")
    }

    "duplicate and blank tags are dropped before they reach the column" {
        store(id = "t-1", tags = listOf("Coffee", "coffee", "", "  ", "work"))

        stack.transactionRepository.getById(transactionId("t-1"))!!.tags shouldBe
            listOf("Coffee", "work")
    }

    "more tags than MAX_TAGS are capped at the storage boundary" {
        store(id = "t-1", tags = (1..TransactionTags.MAX_TAGS + 3).map { "tag$it" })

        stack.transactionRepository.getById(transactionId("t-1"))!!
            .tags.size shouldBe TransactionTags.MAX_TAGS
    }

    // The categorized query lists columns explicitly rather than SELECT *, so a forgotten
    // column there would silently blank the fields on the transactions list screen.
    "the categorized list query carries the new fields too" {
        store(id = "t-1", merchant = "Starbucks", tags = listOf("coffee"), rule = RecurringRuleId("rule-1"))

        val listed = stack.transactionRepository.getAllCategorized().first().single().transaction

        listed.merchant shouldBe "Starbucks"
        listed.tags shouldBe listOf("coffee")
        listed.recurringRuleId shouldBe RecurringRuleId("rule-1")
    }
})
