package com.georgeci.moneysurfer.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.navigation3.runtime.NavEntry
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Which pane each back-stack entry lands in — the half of `SurferPaneSceneStrategy` that decides
 * whether a screen draws a top app bar, a back arrow and a FAB (issue #388). The scaffold itself
 * belongs to the Material delegate and is not re-tested here.
 */
class SurferPaneSceneStrategyTest : StringSpec({

    "a list on its own is the list pane, with nothing to go back to inside it" {
        listOf(listEntry("accounts")).calculateSurferPanes() shouldBe mapOf(
            "accounts" to SurferPane(role = SurferPaneRole.List),
        )
    }

    "a detail opened from its list drops its own chrome" {
        listOf(listEntry("accounts"), detailEntry("account-1")).calculateSurferPanes() shouldBe mapOf(
            "accounts" to SurferPane(role = SurferPaneRole.List),
            "account-1" to SurferPane(role = SurferPaneRole.Detail),
        )
    }

    "a detail opened from another detail keeps a back affordance" {
        val panes = listOf(
            listEntry("settings"),
            detailEntry("about"),
            detailEntry("licenses"),
        ).calculateSurferPanes()

        panes["about"] shouldBe SurferPane(role = SurferPaneRole.Detail, hasPaneBackStack = false)
        panes["licenses"] shouldBe SurferPane(role = SurferPaneRole.Detail, hasPaneBackStack = true)
    }

    "a detail with no list beside it stays a full screen" {
        listOf(plainEntry("dashboard"), detailEntry("account-creation"))
            .calculateSurferPanes()
            .shouldBeEmpty()
    }

    "entries below the first non-pane route are not part of the scaffold" {
        val panes = listOf(
            listEntry("accounts"),
            plainEntry("transaction-filters"),
            listEntry("categories"),
            detailEntry("category-1"),
        ).calculateSurferPanes()

        panes.keys shouldBe setOf("categories", "category-1")
    }

    "an empty back stack has no panes" {
        emptyList<NavEntry<String>>().calculateSurferPanes().shouldBeEmpty()
    }
})

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun listEntry(key: String): NavEntry<String> =
    NavEntry(key = key, metadata = SurferPaneSceneStrategy.listPane(), content = {})

private fun detailEntry(key: String): NavEntry<String> =
    NavEntry(key = key, metadata = SurferPaneSceneStrategy.detailPane(), content = {})

private fun plainEntry(key: String): NavEntry<String> = NavEntry(key = key, content = {})
