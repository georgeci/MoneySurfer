package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneAction
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneScaffold
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneTestTags
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.window.LocalSurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.uikit.window.currentSurferWindowSize
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The two-pane chrome invariant, at Expanded width: a section split across panes still draws
 * *one* top app bar and *one* floating action button (issues #388, #392).
 *
 * Two of each is what the section looked like before `SurferPaneScaffold` learned about
 * [LocalSurferPane], and it is invisible to every phone-width test in the suite — the second bar
 * only exists once two panes are on screen at the same time. This is the regression guard for it.
 *
 * The pane roles are provided directly rather than driven through `SurferPaneSceneStrategy`:
 * which entry becomes which pane is that strategy's job and `SurferPaneSceneStrategyTest` in
 * `:navigation` covers it. What is only observable from a composition — what the roles make the
 * screens *render* — is what this asserts.
 *
 * Lives in `composeApp` for the same reason `SurferWindowSizeCompositionTest` does: it is where
 * `libs.compose.uiTest` is wired.
 */
@OptIn(ExperimentalTestApi::class)
class SurferPaneChromeTest : StringSpec({

    "a list and detail pane side by side render one top app bar and one FAB" {
        runComposeUiTest {
            setContent { ExpandedWindow { TwoPaneSection() } }
            waitForIdle()

            onAllNodesWithTag(SurferPaneTestTags.TopAppBar).fetchSemanticsNodes().size shouldBe 1
            onAllNodesWithTag(SurferPaneTestTags.Fab).fetchSemanticsNodes().size shouldBe 1
        }
    }

    "an inline panel beside the two panes adds no chrome of its own" {
        runComposeUiTest {
            setContent {
                ExpandedWindow {
                    TwoPaneSection {
                        Pane(SurferPaneRole.Extra, "Add transaction") {
                            Text("Panel content")
                        }
                    }
                }
            }
            waitForIdle()

            onAllNodesWithTag(SurferPaneTestTags.TopAppBar).fetchSemanticsNodes().size shouldBe 1
            onAllNodesWithTag(SurferPaneTestTags.Fab).fetchSemanticsNodes().size shouldBe 1
        }
    }

    "the same screen on its own still draws its own chrome" {
        runComposeUiTest {
            setContent {
                ExpandedWindow {
                    Pane(SurferPaneRole.Single, "Accounts") { Text("Single pane content") }
                }
            }
            waitForIdle()

            onAllNodesWithTag(SurferPaneTestTags.TopAppBar).fetchSemanticsNodes().size shouldBe 1
            onAllNodesWithTag(SurferPaneTestTags.Fab).fetchSemanticsNodes().size shouldBe 1
        }
    }

    "the window the panes are asserted at is an Expanded one" {
        runComposeUiTest {
            var observed: SurferWindowSize? = null
            setContent { ExpandedWindow { observed = currentSurferWindowSize() } }
            waitForIdle()

            observed shouldBe SurferWindowSize.Expanded
        }
    }
})

/** A themed composition in a window wide enough for two panes — see [WINDOW_WIDTH_PX]. */
@Composable
private fun ExpandedWindow(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWindowInfo provides FixedWindowInfo(IntSize(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX)),
        LocalDensity provides Density(1f),
    ) {
        AppTheme { content() }
    }
}

/**
 * A list pane beside its detail pane, laid out the way `SurferPaneScene` lays them out.
 *
 * @param extra an optional third column, for the inline add panel case.
 */
@Composable
private fun TwoPaneSection(extra: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.width(LIST_PANE_WIDTH.dp).fillMaxHeight()) {
            Pane(SurferPaneRole.List, "Accounts") { Text("List pane content") }
        }
        Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Pane(SurferPaneRole.Detail, "Everyday") { Text("Detail pane content") }
        }
        extra()
    }
}

/** One screen composing in [role], built the way every pane-aware screen in the app is. */
@Composable
private fun Pane(role: SurferPaneRole, title: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSurferPane provides SurferPane(role = role)) {
        SurferPaneScaffold(
            title = title,
            onBack = {},
            primaryAction = SurferPaneAction(label = "Add", onClick = {}),
        ) { content() }
    }
}

/** 1024 dp at 1x — a tablet in landscape, comfortably inside `SurferWindowSize.Expanded`. */
private const val WINDOW_WIDTH_PX = 1024
private const val WINDOW_HEIGHT_PX = 800

/** `SurferPaneSceneStrategy.ListPanePreferredWidth`. */
private const val LIST_PANE_WIDTH = 300

/** Only [containerSize] matters here; `WindowInfo` defaults the rest. */
private class FixedWindowInfo(override val containerSize: IntSize) : WindowInfo {
    override val isWindowFocused: Boolean = true
}
