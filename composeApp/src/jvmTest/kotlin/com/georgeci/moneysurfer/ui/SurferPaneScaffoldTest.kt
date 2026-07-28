package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneAction
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneScaffold
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarTestTags
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.window.LocalSurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val TITLE = "Account"
private const val ADD_LABEL = "Add transaction"

/**
 * Acceptance cover for issue #388: what a screen's chrome does once the pane host tells it which
 * pane it is in. Driven through `SurferPaneScaffold` rather than through a feature screen, because
 * every converted screen routes its top bar and FAB through this one composable.
 *
 * The FAB is identified by its content description — `SurferAddFab` sets one from the label, and
 * the detail pane's header button does not, so the two are distinguishable despite sharing text.
 */
@OptIn(ExperimentalTestApi::class)
class SurferPaneScaffoldTest : StringSpec({

    "a full-window screen keeps its back arrow and its FAB" {
        runComposeUiTest {
            setContent { PaneScaffold(SurferPane()) }

            onNodeWithTag(SurferToolbarTestTags.Back).assertExists()
            onAllNodesWithContentDescription(ADD_LABEL).fetchSemanticsNodes().size shouldBe 1
        }
    }

    "a list pane keeps its back arrow and its FAB" {
        runComposeUiTest {
            setContent { PaneScaffold(SurferPane(role = SurferPaneRole.List)) }

            onNodeWithTag(SurferToolbarTestTags.Back).assertExists()
            onAllNodesWithContentDescription(ADD_LABEL).fetchSemanticsNodes().size shouldBe 1
        }
    }

    "a detail pane beside its list drops the back arrow and the FAB" {
        runComposeUiTest {
            setContent { PaneScaffold(SurferPane(role = SurferPaneRole.Detail)) }

            onAllNodesWithTag(SurferToolbarTestTags.Back).fetchSemanticsNodes().size shouldBe 0
            onAllNodesWithContentDescription(ADD_LABEL).fetchSemanticsNodes().size shouldBe 0
            // The action survives as a header button rather than disappearing with the FAB.
            onNodeWithText(ADD_LABEL).assertExists()
            onNodeWithText(TITLE).assertExists()
        }
    }

    "a detail pane opened from another detail keeps its back arrow" {
        runComposeUiTest {
            setContent {
                PaneScaffold(SurferPane(role = SurferPaneRole.Detail, hasPaneBackStack = true))
            }

            onNodeWithTag(SurferToolbarTestTags.Back).assertExists()
            onAllNodesWithContentDescription(ADD_LABEL).fetchSemanticsNodes().size shouldBe 0
        }
    }
})

@Composable
private fun PaneScaffold(pane: SurferPane) {
    AppTheme {
        CompositionLocalProvider(LocalSurferPane provides pane) {
            SurferPaneScaffold(
                title = TITLE,
                onBack = {},
                primaryAction = SurferPaneAction(label = ADD_LABEL, onClick = {}),
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) { Text("body") }
            }
        }
    }
}
