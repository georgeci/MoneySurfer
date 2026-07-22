package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The backfill contract. These are not "does the object exist" tests — the migration writes
 * these exact values into every pre-existing row, so a change here silently repaints every
 * category a user already has.
 */
class CategoryAppearanceTest : StringSpec({

    "the icon and hue catalogues are the same length, so a slot always resolves both" {
        CategoryAppearance.ICON_KEYS shouldHaveSize CategoryAppearance.HUES.size
    }

    "every hue sits on the wheel" {
        CategoryAppearance.HUES.forEach { hue ->
            CategoryAppearance.isStoredHue(hue) shouldBe true
        }
    }

    "the default for an id is stable across calls" {
        val id = "8f14e45f-ceea-467a-9f76-1a1b2c3d4e5f"
        CategoryAppearance.defaultIconKey(id) shouldBe CategoryAppearance.defaultIconKey(id)
        CategoryAppearance.defaultHue(id) shouldBe CategoryAppearance.defaultHue(id)
    }

    "the default is drawn from the catalogue, never invented" {
        listOf("a", "b", "", "c-1", "8f14e45f-ceea").forEach { id ->
            (CategoryAppearance.defaultIconKey(id) in CategoryAppearance.ICON_KEYS) shouldBe true
            (CategoryAppearance.defaultHue(id) in CategoryAppearance.HUES) shouldBe true
        }
    }

    "different ids spread across the palette rather than collapsing onto one colour" {
        // The whole point of hashing the id instead of leaving the backfill blank.
        val hues = (1..64).map { CategoryAppearance.defaultHue("category-$it") }.toSet()
        hues.size shouldBeGreaterThan 1
    }

    "icon and hue come from the same slot, so a backfilled row keeps its old pairing" {
        listOf("c-1", "c-2", "c-3", "groceries").forEach { id ->
            val slot = CategoryAppearance.paletteSlot(id)
            CategoryAppearance.defaultIconKey(id) shouldBe CategoryAppearance.ICON_KEYS[slot]
            CategoryAppearance.defaultHue(id) shouldBe CategoryAppearance.HUES[slot]
        }
    }

    "the system Transfer category keeps its fixed appearance whatever its id hashes to" {
        val kind = CategorySystemKind.TRANSFER
        CategoryAppearance.defaultIconKey("anything", kind) shouldBe CategoryAppearance.TRANSFER_ICON_KEY
        CategoryAppearance.defaultHue("anything", kind) shouldBe CategoryAppearance.TRANSFER_HUE
        CategoryAppearance.defaultIconKey("anything") shouldNotBe CategoryAppearance.TRANSFER_ICON_KEY
    }

    "the unset sentinels are not mistaken for stored values" {
        CategoryAppearance.isStoredHue(CategoryAppearance.UNSET_HUE) shouldBe false
        CategoryAppearance.isStoredHue(CategoryAppearance.HUE_DEGREES) shouldBe false
        CategoryAppearance.isStoredIconKey("") shouldBe false
        CategoryAppearance.isStoredIconKey("  ") shouldBe false
        CategoryAppearance.isStoredIconKey("tag") shouldBe true
    }

    "a new Category defaults to the same appearance the backfill would have written" {
        val category = aCategory(id = categoryId("c-42"))

        category.iconKey shouldBe CategoryAppearance.defaultIconKey("c-42")
        category.hue shouldBe CategoryAppearance.defaultHue("c-42")
    }
})
