package com.georgeci.moneysurfer.navigation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The three keys a picker sheet writes its answer under.
 *
 * The string is the whole contract: the sheet writes under it and the screen that opened the sheet
 * reads under it, in different files with no shared symbol between them at runtime. Two keys that
 * collided would deliver an account id to the category picker's caller — and renaming one silently
 * makes the picker look like the user cancelled.
 */
class NavigationResultKeysTest : StringSpec({

    "each picker writes under its own key" {
        listOf(
            AccountPickerResultKey.resultKey,
            AccountPickerTransferResultKey.resultKey,
            CategoryPickerResultKey.resultKey,
        ) shouldContainExactly listOf(
            "AccountPickerResult",
            "AccountPickerTransferResult",
            "CategoryPickerResult",
        )
    }

    "the account picker returns an account id" {
        AccountPickerResultKey.serializer.descriptor.serialName shouldBe
            "com.georgeci.moneysurfer.domain.primitives.AccountId"
    }

    "the category picker returns a category id" {
        CategoryPickerResultKey.serializer.descriptor.serialName shouldBe
            "com.georgeci.moneysurfer.domain.primitives.CategoryId"
    }

    // The footer carries no data — the host reads it as "switch this entry to a transfer".
    "the transfer shortcut returns a bare flag" {
        AccountPickerTransferResultKey.serializer.descriptor.serialName shouldBe "kotlin.Boolean"
    }
})
