package com.georgeci.moneysurfer.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
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

        // Only `licenses` composes — popping it lands back on `about` rather than on the
        // placeholder, so the arrow it draws goes somewhere.
        panes["licenses"] shouldBe SurferPane(role = SurferPaneRole.Detail, hasPaneBackStack = true)
    }

    "a list pushed on top of a detail leaves that detail its back affordance" {
        // Accounts' "See all" pushes the transactions list from the account detail, so the detail
        // pane ends up beside a list that is not its own and must stay dismissable.
        val panes = listOf(
            listEntry("accounts"),
            detailEntry("account-1"),
            listEntry("transactions"),
        ).calculateSurferPanes()

        panes["account-1"] shouldBe SurferPane(
            role = SurferPaneRole.Detail,
            hasPaneBackStack = true,
        )
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

    // Issue #391: the panes used to take whatever the Material directive defaulted to (360 dp).
    "the list and panel panes ask for the widths the design specifies" {
        (SurferPaneSceneStrategy.ListPanePreferredWidth in 270.dp..320.dp) shouldBe true
        (SurferPaneSceneStrategy.InlinePanelPreferredWidth in 300.dp..340.dp) shouldBe true

        // The width reaches the scaffold as entry metadata, under a key the delegate keeps
        // internal — assert the value is carried rather than the spelling of the key.
        listEntry("accounts").metadata.values shouldContain
            SurferPaneSceneStrategy.ListPanePreferredWidth
        extraEntry("add-transaction").metadata.values shouldContain
            SurferPaneSceneStrategy.InlinePanelPreferredWidth
    }

    "an inline panel is the extra pane and stays dismissable" {
        val panes = listOf(
            listEntry("accounts"),
            detailEntry("account-1"),
            extraEntry("add-transaction"),
        ).calculateSurferPanes()

        panes shouldBe mapOf(
            "accounts" to SurferPane(role = SurferPaneRole.List),
            // The panel does not displace the account it was opened from, so the account keeps no
            // back affordance of its own — its list is still beside it.
            "account-1" to SurferPane(role = SurferPaneRole.Detail),
            "add-transaction" to SurferPane(role = SurferPaneRole.Extra, hasPaneBackStack = true),
        )
    }

    "a list pushed above an inline panel still gives the detail its back affordance" {
        val panes = listOf(
            listEntry("accounts"),
            detailEntry("account-1"),
            extraEntry("add-transaction"),
            listEntry("transactions"),
        ).calculateSurferPanes()

        panes["account-1"] shouldBe SurferPane(
            role = SurferPaneRole.Detail,
            hasPaneBackStack = true,
        )
    }

    "the extra pane draws no section chrome" {
        SurferPane(role = SurferPaneRole.Extra).ownsSectionChrome shouldBe false
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun extraEntry(key: String): NavEntry<String> =
    NavEntry(key = key, metadata = SurferPaneSceneStrategy.extraPane(), content = {})

private fun plainEntry(key: String): NavEntry<String> = NavEntry(key = key, content = {})
