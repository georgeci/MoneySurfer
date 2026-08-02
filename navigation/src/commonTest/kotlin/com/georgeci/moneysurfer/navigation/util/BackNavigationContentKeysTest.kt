package com.georgeci.moneysurfer.navigation.util

import androidx.navigation3.runtime.NavEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Which entries may draw a back affordance at all — the input to
 * [rememberBackNavigationNavEntryDecorator], and thereby to every toolbar's navigation icon.
 *
 * The rule is only interesting at the bottom of the stack: a section the drawer reset to has
 * nowhere to go, the same section pushed from the dashboard on a phone does.
 */
class BackNavigationContentKeysTest : StringSpec({

    "a section the drawer reset to has nothing to pop" {
        backNavigationContentKeys(listOf("accounts"), ::entry).shouldBeEmpty()
    }

    "the same section pushed from another one has" {
        backNavigationContentKeys(listOf("dashboard", "accounts"), ::entry) shouldBe
            setOf("accounts")
    }

    "the list pane of a two-pane section keeps no affordance while its detail gets one" {
        backNavigationContentKeys(listOf("accounts", "account-1"), ::entry) shouldBe
            setOf("account-1")
    }

    "an empty back stack has nothing to pop" {
        backNavigationContentKeys(emptyList(), ::entry).shouldBeEmpty()
    }

    "the keys are the provider's content keys, not the routes" {
        val keyed = { route: String -> NavEntry(key = route, contentKey = "content:$route") {} }

        backNavigationContentKeys(listOf("dashboard", "accounts"), keyed) shouldBe
            setOf("content:accounts")
    }
})

private fun entry(key: String): NavEntry<String> = NavEntry(key = key, content = {})
