package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.TransactionId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TransactionReferenceTest : StringSpec({

    "uses the trailing hex digits of the id" {
        TransactionId("6f9619ff-8b86-d011-b42d-00cf4fc98213").reference shouldBe "TX-8213"
    }

    "uppercases hex letters" {
        TransactionId("6f9619ff-8b86-d011-b42d-00cf4fc9821a").reference shouldBe "TX-821A"
    }

    "is stable for the same id" {
        val id = TransactionId.uuid()
        id.reference shouldBe id.reference
    }

    // Ids from a statement import are not guaranteed to be UUIDs.
    "keeps only hex digits from a non-UUID id" {
        TransactionId("imported/tx#42-ff").reference shouldBe "TX-42FF"
    }

    "pads ids with fewer than four hex digits" {
        TransactionId("z-7").reference shouldBe "TX-0007"
    }

    "never returns a bare prefix for an id with no hex digits" {
        TransactionId("zzz").reference shouldBe "TX-0000"
    }

    "distinguishes ids differing in their trailing digits" {
        TransactionId("aaaa-1111").reference shouldNotBe TransactionId("aaaa-2222").reference
    }
})
