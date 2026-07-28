package com.georgeci.moneysurfer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.config.ConfigDebugLayerCell
import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigContent
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigEvent
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigState
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

/**
 * Desktop UI cover for the QA configuration panel — see docs/testing/testing-strategy.md.
 *
 * The availability branch is the reason this file exists: release builds bind
 * `DebugConfigSource.Empty`, which accepts a write and drops it, so a panel that rendered its
 * controls anyway would report success for overrides that were never stored. Only the rendered tree
 * can show that the branch is honoured.
 *
 * The rest of the file guards the same class of silence one level down: which control a key gets is
 * derived from its value kind, and a row that renders the wrong one — a text field where a codec
 * expects `PRESET:Plum`, a switch that ignores the resolved value — still looks like a working
 * panel from the ViewModel's side.
 */
@OptIn(ExperimentalTestApi::class)
class DebugConfigScreenStateTest : StringSpec({

    "an available panel renders the key rows and the reset action" {
        runComposeUiTest {
            setContent {
                DebugConfigContent(
                    state = DebugConfigState(isAvailable = true, rows = listOf(FLAG_ROW)),
                    snackbarHostState = SnackbarHostState(),
                    onEvent = {},
                )
            }

            onNodeWithTag(DebugConfigTestTags.Root).assertIsDisplayed()
            onNodeWithTag(DebugConfigTestTags.row(FLAG_ROW.name)).assertIsDisplayed()
            onNodeWithTag(DebugConfigTestTags.LogsRow).assertIsDisplayed()
            onNodeWithTag(DebugConfigTestTags.ResetAllRow).assertIsDisplayed()
            onAllNodesWithTag(DebugConfigTestTags.Unavailable).assertCountEquals(0)
        }
    }

    "an unavailable panel offers no controls at all, only an explanation" {
        runComposeUiTest {
            setContent {
                DebugConfigContent(
                    // Rows are still supplied: the layer being inert has nothing to do with the
                    // registry, and a screen that showed them would invite writes it would drop.
                    state = DebugConfigState(isAvailable = false, rows = listOf(FLAG_ROW)),
                    snackbarHostState = SnackbarHostState(),
                    onEvent = {},
                )
            }

            onNodeWithTag(DebugConfigTestTags.Unavailable).assertIsDisplayed()
            onAllNodesWithTag(DebugConfigTestTags.Root).assertCountEquals(0)
            onNodeWithTag(DebugConfigTestTags.row(FLAG_ROW.name)).assertDoesNotExist()
            onAllNodesWithTag(DebugConfigTestTags.LogsRow).assertCountEquals(0)
            onAllNodesWithTag(DebugConfigTestTags.ResetAllRow).assertCountEquals(0)
        }
    }

    "a degraded layer is called out above the rows" {
        runComposeUiTest {
            setContent {
                DebugConfigContent(
                    state = DebugConfigState(
                        isAvailable = true,
                        rows = listOf(FLAG_ROW),
                        degradedLayers = listOf("Local"),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onEvent = {},
                )
            }

            onNodeWithTag(DebugConfigTestTags.DegradedBanner).assertIsDisplayed()
        }
    }

    "a boolean key gets a switch and nothing to type into" {
        runComposeUiTest {
            setContent { Panel(FLAG_ROW) }

            onNodeWithTag(DebugConfigTestTags.toggle(FLAG_ROW.name)).assertIsDisplayed()
            // The switch is the whole control: no chips, and nothing to type a raw value into.
            onNode(isSelectable()).assertDoesNotExist()
            onNode(hasSetTextAction()).assertDoesNotExist()
        }
    }

    "the switch asks for the opposite of the value that resolved, in both directions" {
        listOf("true" to "false", "false" to "true").forEach { (effective, asked) ->
            runComposeUiTest {
                val events = mutableListOf<DebugConfigEvent>()
                setContent { Panel(FLAG_ROW.copy(effectiveValue = effective), onEvent = { events += it }) }

                onNodeWithTag(DebugConfigTestTags.toggle(FLAG_ROW.name)).performClick()
                waitForIdle()

                // A switch wired to a constant position rather than to the resolved value passes
                // one half of this and fails the other.
                events shouldContainExactly listOf(DebugConfigEvent.OnOverride(FLAG_ROW.name, asked))
            }
        }
    }

    "a closed-set key gets one chip per choice, with the effective value marked" {
        runComposeUiTest {
            setContent { Panel(PALETTE_ROW) }

            onNodeWithTag(DebugConfigTestTags.choice(PALETTE_ROW.name, PLUM)).assertIsSelected()
            onNodeWithTag(DebugConfigTestTags.choice(PALETTE_ROW.name, MINT)).assertIsNotSelected()
            // The chips exist so nobody has to hand-type `PRESET:Plum`; a text field would put that
            // back on the table.
            onNode(hasSetTextAction()).assertDoesNotExist()
            onAllNodesWithTag(DebugConfigTestTags.toggle(PALETTE_ROW.name)).assertCountEquals(0)
        }
    }

    "tapping a chip asks for that choice" {
        runComposeUiTest {
            val events = mutableListOf<DebugConfigEvent>()
            setContent { Panel(PALETTE_ROW, onEvent = { events += it }) }

            onNodeWithTag(DebugConfigTestTags.choice(PALETTE_ROW.name, MINT)).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DebugConfigEvent.OnOverride(PALETTE_ROW.name, MINT))
        }
    }

    "a free-text key is inspect-only" {
        runComposeUiTest {
            setContent { Panel(LAYERED_ROW) }

            onNodeWithTag(DebugConfigTestTags.row(LAYERED_ROW.name)).assertIsDisplayed()
            // No field, no switch, no chips: a hand-typed value no codec accepts is a routine way
            // to break a key, and the panel has no way to hand it back.
            onNode(hasSetTextAction()).assertDoesNotExist()
            onNode(isSelectable()).assertDoesNotExist()
            onAllNodesWithTag(DebugConfigTestTags.toggle(LAYERED_ROW.name)).assertCountEquals(0)
        }
    }

    "the resolution summary names the effective value and the layer that won" {
        runComposeUiTest {
            setContent { Panel(LAYERED_ROW) }

            // Read back rather than matched against the rendered sentence, so the assertion says
            // what has to be there without pinning the wording of a translated string.
            val summary = textStartingWith("17 · ")

            summary shouldContain "Remote"
            summary shouldNotContain "Local"
        }
    }

    "a layer holding nothing reads differently from one whose value the codec rejects" {
        runComposeUiTest {
            setContent { Panel(LAYERED_ROW) }

            val absent = textStartingWith("Debug: ")
            val rejected = textStartingWith("Local: ")
            val plain = textStartingWith("Remote: ")

            // The layer that simply holds the key: its value, and nothing around it.
            plain shouldBe "Remote: 17"
            // Stored but rejected by the codec — the raw value is still shown, plus a marker the
            // plain cell does not carry. Collapsing this into "absent" hides the one failure the
            // per-layer view exists to expose.
            rejected shouldStartWith "Local: 9000"
            rejected shouldNotBe "Local: 9000"
            // Absent: a word of its own, and never a value the layer does not hold.
            absent shouldNotBe "Debug: "
            absent shouldNotContain "9000"
            absent shouldNotContain "17"
        }
    }

    "an overridden row clears its override when tapped" {
        runComposeUiTest {
            val events = mutableListOf<DebugConfigEvent>()
            setContent { Panel(PALETTE_ROW, onEvent = { events += it }) }

            onNodeWithTag(DebugConfigTestTags.row(PALETTE_ROW.name)).assertHasClickAction()
            onNodeWithTag(DebugConfigTestTags.row(PALETTE_ROW.name)).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DebugConfigEvent.OnClearOverride(PALETTE_ROW.name))
        }
    }

    "a row with no override of its own has nothing to clear" {
        runComposeUiTest {
            val events = mutableListOf<DebugConfigEvent>()
            setContent { Panel(FLAG_ROW, onEvent = { events += it }) }

            onNodeWithTag(DebugConfigTestTags.row(FLAG_ROW.name)).assertHasNoClickAction()
            onNodeWithTag(DebugConfigTestTags.row(FLAG_ROW.name)).performClick()
            waitForIdle()

            events.shouldBeEmpty()
        }
    }

    "the reset-all row asks to drop every override" {
        runComposeUiTest {
            val events = mutableListOf<DebugConfigEvent>()
            setContent { Panel(FLAG_ROW, onEvent = { events += it }) }

            onNodeWithTag(DebugConfigTestTags.ResetAllRow).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DebugConfigEvent.OnResetAllClick)
        }
    }
})

private const val PLUM = "Plum"

private const val MINT = "Mint"

/** A host key, so the row also carries the build-identity note. Not overridden: the Build layer won. */
private val FLAG_ROW = ConfigDebugRow(
    name = "host.transfer_enabled",
    effectiveValue = "true",
    winner = "Build",
    kind = ConfigDebugRowKind.Bool,
    overridden = false,
    hostOwned = true,
    layers = listOf(ConfigDebugLayerCell(layer = "Build", value = "true", undecodable = false)),
)

/** A closed set the tester picked from, so this row can also stand in for the clearable case. */
private val PALETTE_ROW = ConfigDebugRow(
    name = "ui.palette",
    effectiveValue = PLUM,
    winner = "Debug",
    kind = ConfigDebugRowKind.Choice(choices = listOf(PLUM, MINT)),
    overridden = true,
    layers = listOf(ConfigDebugLayerCell(layer = "Debug", value = PLUM, undecodable = false)),
)

/**
 * One key seen from three layers at once: one that never stored it, one whose stored value the
 * codec rejects, and the one that won. Free-text so the row renders no control of its own.
 */
private val LAYERED_ROW = ConfigDebugRow(
    name = "ui.retry_budget",
    effectiveValue = "17",
    winner = "Remote",
    kind = ConfigDebugRowKind.FreeText,
    overridden = false,
    layers = listOf(
        ConfigDebugLayerCell(layer = "Debug", value = null, undecodable = false),
        ConfigDebugLayerCell(layer = "Local", value = "9000", undecodable = true),
        ConfigDebugLayerCell(layer = "Remote", value = "17", undecodable = false),
    ),
)

/** An available panel showing exactly [rows] — the shape every mapping assertion below mounts. */
@Composable
private fun Panel(vararg rows: ConfigDebugRow, onEvent: (DebugConfigEvent) -> Unit = {}) {
    DebugConfigContent(
        state = DebugConfigState(isAvailable = true, rows = rows.toList()),
        snackbarHostState = remember { SnackbarHostState() },
        onEvent = onEvent,
    )
}

/**
 * The whole rendered text of the one leaf node starting with [prefix]. The unmerged tree is what
 * makes it a single line: a clickable row merges every text under it into one node.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.textStartingWith(prefix: String): String =
    onNodeWithText(prefix, substring = true, useUnmergedTree = true).wholeText()

private fun SemanticsNodeInteraction.wholeText(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(separator = "") { it.text }
