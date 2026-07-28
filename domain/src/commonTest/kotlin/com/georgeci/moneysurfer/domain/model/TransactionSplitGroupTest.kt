package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.splitId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Collapsing rows back into receipts — what the recent-activity widget renders and what any other
 * "one line per payment" surface should reuse.
 */
class TransactionSplitGroupTest : StringSpec({

    fun leg(id: String, amount: Long, category: String, split: String = "sp-1") = aTransaction(
        id = transactionId(id),
        money = Money.fromMinor(amount),
        categoryId = categoryId(category),
        splitId = splitId(split),
    )

    "legs of one receipt become a single group holding the whole payment" {
        val groups = listOf(
            leg("leg-a", 3_000, "c-food"),
            leg("leg-b", 400, "c-home"),
        ).groupSplitLegs()

        groups shouldHaveSize 1
        groups.single().isSplit shouldBe true
        groups.single().total shouldBe Money.fromMinor(3_400)
        groups.single().categoryCount shouldBe 2
        // The topmost leg stands for the group, so tapping the row opens a real transaction.
        groups.single().primary.id shouldBe transactionId("leg-a")
    }

    "a group takes the position of its first leg and the order around it is kept" {
        val groups = listOf(
            aTransaction(id = transactionId("coffee")),
            leg("leg-a", 3_000, "c-food"),
            aTransaction(id = transactionId("rent")),
            leg("leg-b", 400, "c-home"),
            aTransaction(id = transactionId("salary")),
        ).groupSplitLegs()

        // Legs are matched across the whole list, not only between neighbours: nothing stops
        // another row from sorting between two legs of the same receipt.
        groups.map { it.primary.id.value } shouldBe listOf("coffee", "leg-a", "rent", "salary")
        groups[1].legs shouldHaveSize 2
    }

    "two receipts stay two groups" {
        val groups = listOf(
            leg("leg-a", 3_000, "c-food", split = "sp-1"),
            leg("leg-b", 400, "c-food", split = "sp-2"),
        ).groupSplitLegs()

        groups.map { it.total } shouldBe listOf(Money.fromMinor(3_000), Money.fromMinor(400))
    }

    "an ordinary transaction is a group of one and is not a split" {
        val groups = listOf(aTransaction(id = transactionId("coffee"))).groupSplitLegs()

        groups.single().isSplit shouldBe false
        groups.single().categoryCount shouldBe 1
    }

    "legs left uncategorized count as one category between them" {
        val groups = listOf(
            leg("leg-a", 3_000, "c-food").copy(categoryId = null),
            leg("leg-b", 400, "c-home").copy(categoryId = null),
        ).groupSplitLegs()

        groups.single().categoryCount shouldBe 1
    }
})
