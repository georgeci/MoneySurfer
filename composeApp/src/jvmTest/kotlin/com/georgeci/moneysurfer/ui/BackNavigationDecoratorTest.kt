package com.georgeci.moneysurfer.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import com.georgeci.moneysurfer.navigation.util.rememberBackNavigationNavEntryDecorator
import com.georgeci.moneysurfer.uikit.navigation.LocalSurferCanNavigateBack
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * That the back-affordance flag actually *reaches* a screen.
 *
 * `BackNavigationContentKeysTest` in `:navigation` covers which entries the flag is `true` for, and
 * `SurferPaneScaffoldTest` covers what a toolbar does with it once it is set. Neither notices if
 * the decorator stops being applied — drop it from `AppNavGraph`'s `entryDecorators` and every
 * drawer section quietly gets its dead back arrow back with the whole suite still green. This is
 * the join between the two halves.
 *
 * Lives in `composeApp` for the same reason `SurferPaneChromeTest` does: it is where
 * `libs.compose.uiTest` is wired.
 */
@OptIn(ExperimentalTestApi::class)
class BackNavigationDecoratorTest : StringSpec({

    "the decorator gives each entry the affordance its place on the stack allows" {
        val seen = mutableMapOf<String, Boolean>()

        runComposeUiTest {
            setContent {
                val entries = remember { listOf(probe("accounts", seen), probe("account-1", seen)) }
                // What `backNavigationContentKeys` produces for the stack [accounts, account-1]:
                // the list is the bottom entry, the detail above it is not.
                val decorator = rememberBackNavigationNavEntryDecorator<String>(setOf("account-1"))

                rememberDecoratedNavEntries(entries, listOf(decorator)).forEach { it.Content() }
            }
            waitForIdle()
        }

        seen shouldBe mapOf("accounts" to false, "account-1" to true)
    }
})

/** A [NavEntry] whose whole content is recording the flag it was composed with. */
private fun probe(key: String, seen: MutableMap<String, Boolean>): NavEntry<String> =
    NavEntry(key = key) { seen[key] = LocalSurferCanNavigateBack.current }
