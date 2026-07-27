package com.georgeci.moneysurfer.domain.preferences

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class NumberFormatStyleTest : StringSpec({

    "each style writes itself out the way the Preferences pill shows it" {
        // `sample` is the label, not a description of it, so these strings are user-visible text.
        NumberFormatStyle.CommaGroupDotDecimal.sample shouldBe "1,234.56"
        NumberFormatStyle.DotGroupCommaDecimal.sample shouldBe "1.234,56"
    }

    "the space-grouped style uses a non-breaking space" {
        // Pinned explicitly: a plain space looks identical in a diff and would let the number wrap
        // mid-group in the pill.
        NumberFormatStyle.SpaceGroupCommaDecimal.groupSeparator shouldBe "\u00A0"
        NumberFormatStyle.SpaceGroupCommaDecimal.sample shouldBe "1\u00A0234,56"
    }
})
