package com.georgeci.moneysurfer.feature.category.creation

import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CategoryCreationStateTest : StringSpec({

    "blank names cannot be saved and show an error only after being touched" {
        CategoryCreationState(name = "", nameTouched = false).run {
            canSave shouldBe false
            nameMissing shouldBe false
        }
        CategoryCreationState(name = "  ", nameTouched = true).run {
            canSave shouldBe false
            nameMissing shouldBe true
        }
    }

    "a named idle form can save but a loading form cannot" {
        CategoryCreationState(name = "Food").canSave shouldBe true
        CategoryCreationState(name = "Food", isLoading = true).canSave shouldBe false
    }

    "the name counter appears exactly at its threshold" {
        CategoryCreationState(
            name = "x".repeat(CategoryCreationState.NAME_COUNTER_THRESHOLD - 1),
        ).showNameCounter shouldBe false
        CategoryCreationState(
            name = "x".repeat(CategoryCreationState.NAME_COUNTER_THRESHOLD),
        ).showNameCounter shouldBe true
    }

    "monthly cap is shown for expenses but hidden for income" {
        CategoryCreationState(type = CategoryTypeUi.Expense).showCap shouldBe true
        CategoryCreationState(type = CategoryTypeUi.Income).showCap shouldBe false
    }

    "positive cap text is converted from major units into Money" {
        CategoryCreationState(cap = "125.45").capAsMoney shouldBe Money.fromMinor(12_545)
    }

    "empty invalid zero and negative caps mean no category cap" {
        CategoryCreationState(cap = "").capAsMoney.shouldBeNull()
        CategoryCreationState(cap = "not-a-number").capAsMoney.shouldBeNull()
        CategoryCreationState(cap = "0").capAsMoney.shouldBeNull()
        CategoryCreationState(cap = "-10").capAsMoney.shouldBeNull()
    }

    "fractional cap input follows Money rounding" {
        CategoryCreationState(cap = "1.999").capAsMoney shouldBe Money.fromDouble(1.999)
    }
})
