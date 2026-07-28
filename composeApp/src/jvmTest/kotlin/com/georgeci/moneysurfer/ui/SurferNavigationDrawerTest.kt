package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerItem
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerSection
import com.georgeci.moneysurfer.uikit.components.base.SurferNavigationDrawer
import com.georgeci.moneysurfer.uikit.components.base.SurferNavigationDrawerTestTags
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Acceptance cover for the drawer half of issue #389 — the three things
 * `NavigationSuiteScaffold` does not model, and the selection behaviour the caller relies on.
 *
 * The visual layout is pinned by `SurferNavigationScreenshotTest` in `:uikit`; this is about
 * behaviour, so it asserts on semantics rather than on pixels.
 */
@OptIn(ExperimentalTestApi::class)
class SurferNavigationDrawerTest : StringSpec({

    "the brand header and the user block are both rendered" {
        runComposeUiTest {
            setContent { Drawer() }

            onNodeWithTag(SurferNavigationDrawerTestTags.Root).assertExists()
            onNodeWithText(BRAND).assertExists()
            onNodeWithText(USER).assertExists()
            onNodeWithText(WORKSPACE).assertExists()
        }
    }

    "a section caption is rendered uppercase" {
        runComposeUiTest {
            setContent { Drawer() }

            onNodeWithText(SECTION.uppercase()).assertExists()
            // The natural-case string is the caller's; only the rendered caption is uppercased.
            onAllNodesWithText(SECTION).fetchSemanticsNodes().size shouldBe 0
        }
    }

    "the selected destination is the only one marked selected" {
        runComposeUiTest {
            setContent { Drawer() }

            onNodeWithText(SELECTED_ITEM).assertIsSelected()
            onNodeWithText(OTHER_ITEM).assertIsNotSelected()
            onNodeWithText(MANAGED_ITEM).assertIsNotSelected()
        }
    }

    "clicking a destination reports that destination" {
        runComposeUiTest {
            var clicked: String? = null
            setContent { Drawer(onItemClick = { clicked = it }) }

            onNodeWithText(MANAGED_ITEM).performClick()

            clicked shouldBe MANAGED_ITEM
        }
    }

    "the user block reports a click when it is given a handler" {
        runComposeUiTest {
            var userClicks = 0
            setContent { Drawer(onUserClick = { userClicks++ }) }

            onNodeWithTag(SurferNavigationDrawerTestTags.User).assertHasClickAction()
            onNodeWithTag(SurferNavigationDrawerTestTags.User).performClick()

            userClicks shouldBe 1
        }
    }

    "the user block is inert without a handler" {
        runComposeUiTest {
            setContent { Drawer(onUserClick = null) }

            // The footer is a label until the caller gives it somewhere to go. Asserted on the
            // semantics rather than by clicking it: `performClick` needs no click action, so a
            // click that reports nothing would pass even if the guard were dropped.
            onNodeWithTag(SurferNavigationDrawerTestTags.User).assertHasNoClickAction()
            onNodeWithText(USER).assertExists()
        }
    }
})

private const val BRAND = "MoneySurfer"
private const val USER = "Georgy"
private const val WORKSPACE = "Household budget"
private const val SECTION = "Manage"
private const val SELECTED_ITEM = "Dashboard"
private const val OTHER_ITEM = "Accounts"
private const val MANAGED_ITEM = "Settings"
private val DRAWER_HEIGHT = 520.dp

@Composable
private fun Drawer(
    onItemClick: (String) -> Unit = {},
    onUserClick: (() -> Unit)? = {},
) {
    AppTheme {
        Box(modifier = Modifier.height(DRAWER_HEIGHT)) {
            SurferNavigationDrawer(
                sections = listOf(
                    SurferDrawerSection(
                        items = listOf(
                            item(SELECTED_ITEM, selected = true, onItemClick = onItemClick),
                            item(OTHER_ITEM, onItemClick = onItemClick),
                        ),
                    ),
                    SurferDrawerSection(
                        label = SECTION,
                        items = listOf(item(MANAGED_ITEM, onItemClick = onItemClick)),
                    ),
                ),
                brand = BRAND,
                userName = USER,
                workspaceName = WORKSPACE,
                onUserClick = onUserClick,
            )
        }
    }
}

private fun item(
    label: String,
    selected: Boolean = false,
    onItemClick: (String) -> Unit,
) = SurferDrawerItem(
    label = label,
    icon = SurferIcons.Dashboard,
    selected = selected,
    onClick = { onItemClick(label) },
)
