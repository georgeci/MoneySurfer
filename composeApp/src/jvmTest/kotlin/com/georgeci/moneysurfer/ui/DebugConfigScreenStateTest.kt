package com.georgeci.moneysurfer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.config.ConfigDebugLayerCell
import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigContent
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigState
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigTestTags
import io.kotest.core.spec.style.StringSpec

/**
 * Desktop UI cover for the QA configuration panel — see docs/testing/testing-strategy.md.
 *
 * The availability branch is the reason this file exists: release builds bind
 * `DebugConfigSource.Empty`, which accepts a write and drops it, so a panel that rendered its
 * controls anyway would report success for overrides that were never stored. Only the rendered tree
 * can show that the branch is honoured.
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
})

private val FLAG_ROW = ConfigDebugRow(
    name = "host.transfer_enabled",
    effectiveValue = "true",
    winner = "Build",
    kind = ConfigDebugRowKind.Bool,
    overridden = false,
    hostOwned = true,
    layers = listOf(ConfigDebugLayerCell(layer = "Build", value = "true", undecodable = false)),
)
