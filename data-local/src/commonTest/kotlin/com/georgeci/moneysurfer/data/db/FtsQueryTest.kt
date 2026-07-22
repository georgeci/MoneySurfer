package com.georgeci.moneysurfer.data.db

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FtsQueryTest : StringSpec({

    "appends a prefix wildcard to every token" {
        FtsQuery.fromUserInput("taxi airport") shouldBe "taxi* airport*"
    }

    "collapses punctuation and repeated separators into token boundaries" {
        FtsQuery.fromUserInput("  coffee,  \"to-go\"  ") shouldBe "coffee* to* go*"
    }

    "keeps Cyrillic and digits intact" {
        FtsQuery.fromUserInput("Пятёрочка 2026") shouldBe "Пятёрочка* 2026*"
    }

    "defuses FTS operators by wildcarding them" {
        FtsQuery.fromUserInput("OR NOT NEAR") shouldBe "OR* NOT* NEAR*"
    }

    "returns null when nothing searchable is left" {
        FtsQuery.fromUserInput("") shouldBe null
        FtsQuery.fromUserInput("   ") shouldBe null
        FtsQuery.fromUserInput("!!! ??? ***") shouldBe null
    }
})
