package com.georgeci.moneysurfer.feature.category

import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.tokens.AppColors
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * uikit deliberately does not depend on domain, so the icon-key and hue catalogues exist twice:
 * once as the values the domain stores and backfills, once as the values uikit knows how to
 * render. Nothing at compile time ties them together — this spec does, from the one module that
 * sees both. If it fails, a stored category resolves to the fallback and quietly renders the
 * wrong icon or colour.
 */
class CategoryPaletteParityTest : StringSpec({

    "the domain's icon keys are exactly the ones uikit can render" {
        SurferCategoryPalette.iconKeys shouldBe CategoryAppearance.ICON_KEYS
    }

    "the domain's hues are exactly the ones uikit has tints for" {
        SurferCategoryPalette.hues shouldBe CategoryAppearance.HUES
    }

    "the Transfer marker agrees on both sides" {
        SurferCategoryPalette.TRANSFER_ICON_KEY shouldBe CategoryAppearance.TRANSFER_ICON_KEY
    }

    "every catalogue key resolves to an icon and every catalogue hue to a tint" {
        CategoryAppearance.ICON_KEYS.forEach { key ->
            (SurferCategoryPalette.iconForKey(key) != null) shouldBe true
        }
        CategoryAppearance.HUES.forEach { hue ->
            (SurferCategoryPalette.tintIndexForHue(hue) != null) shouldBe true
        }
    }

    "both themes carry a tint for every hue in the catalogue" {
        AppColors.CategoryTints.size shouldBe CategoryAppearance.HUES.size
        AppColors.Dark.CategoryTints.size shouldBe CategoryAppearance.HUES.size
    }

    "the unset sentinels fall back rather than resolving to a real slot" {
        SurferCategoryPalette.iconForKey("") shouldBe null
        SurferCategoryPalette.tintIndexForHue(CategoryAppearance.UNSET_HUE) shouldBe null
    }

    "a stored appearance lands on its own slot, and the id-hash fallback agrees with domain" {
        SurferCategoryPalette.iconForKey(CategoryAppearance.ICON_KEYS[3]) shouldBe
            SurferCategoryPalette.icons[3]
        SurferCategoryPalette.tintIndexForHue(CategoryAppearance.HUES[3]) shouldBe 3

        SurferCategoryPalette.iconFor("c-1") shouldBe
            SurferCategoryPalette.iconForKey(CategoryAppearance.defaultIconKey("c-1"))
        SurferCategoryPalette.tintIndexFor("c-1") shouldBe
            SurferCategoryPalette.tintIndexForHue(CategoryAppearance.defaultHue("c-1"))
    }

    "an off-palette hue snaps to the nearest tint instead of disappearing" {
        // 165 is 3 degrees from the mint hue (162) and far from everything else.
        SurferCategoryPalette.tintIndexForHue(165) shouldBe 0
        // 355 wraps past 360 to reach clay (8) rather than settling on rose (340).
        SurferCategoryPalette.tintIndexForHue(355) shouldBe 6
    }
})
