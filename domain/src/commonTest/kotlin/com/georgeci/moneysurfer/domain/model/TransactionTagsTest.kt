package com.georgeci.moneysurfer.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TransactionTagsTest : StringSpec({

    "trims surrounding whitespace" {
        TransactionTags.normalize(listOf("  coffee  ")) shouldBe listOf("coffee")
    }

    "drops blank entries" {
        TransactionTags.normalize(listOf("coffee", "", "   ", "travel")) shouldBe
            listOf("coffee", "travel")
    }

    // The CSV storage column would split such a tag in two on the next read.
    "strips the CSV separator instead of splitting or rejecting" {
        TransactionTags.normalize(listOf("coffee,travel")) shouldBe listOf("coffee travel")
    }

    "collapses whitespace runs left behind by separator stripping" {
        TransactionTags.normalize(listOf("coffee , travel")) shouldBe listOf("coffee travel")
    }

    "removes case-insensitive duplicates keeping the first spelling" {
        TransactionTags.normalize(listOf("Coffee", "coffee", "COFFEE")) shouldBe listOf("Coffee")
    }

    "preserves the order the user typed" {
        TransactionTags.normalize(listOf("travel", "coffee", "work")) shouldBe
            listOf("travel", "coffee", "work")
    }

    "caps at MAX_TAGS" {
        val many = (1..TransactionTags.MAX_TAGS + 5).map { "tag$it" }
        TransactionTags.normalize(many) shouldHaveSize TransactionTags.MAX_TAGS
    }

    "is idempotent" {
        val once = TransactionTags.normalize(listOf(" Coffee ", "coffee", "a,b"))
        TransactionTags.normalize(once) shouldBe once
    }

    "empty input stays empty" {
        TransactionTags.normalize(emptyList()) shouldBe emptyList()
    }
})
