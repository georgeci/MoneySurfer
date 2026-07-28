package com.georgeci.moneysurfer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.feature.dashboard.customize.DashboardCardStyleSheetContent
import com.georgeci.moneysurfer.feature.dashboard.customize.DashboardCustomizeContent
import com.georgeci.moneysurfer.feature.dashboard.customize.DashboardCustomizeEvent
import com.georgeci.moneysurfer.feature.dashboard.customize.DashboardCustomizeTestTags
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.utils.AsyncState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Desktop UI cover for the dashboard customize screen — see docs/testing/testing-strategy.md.
 *
 * The reorder case is the reason this file exists: the drag maths in `SurferReorderableList` reads
 * live `LazyListState` geometry, so nothing below the semantics tree can tell whether a gesture
 * lands on the right row.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardCustomizeScreenStateTest : StringSpec({

    "the loaded screen splits widgets into the on-dashboard and available sections" {
        runComposeUiTest {
            setContent {
                DashboardCustomizeContent(state = AsyncState.Content(GOALS_OFF), onEvent = {})
            }

            onNodeWithTag(DashboardCustomizeTestTags.Root).assertIsDisplayed()
            onNodeWithTag(DashboardCustomizeTestTags.EnabledHeader).assertIsDisplayed()
            onNodeWithTag(DashboardCustomizeTestTags.AvailableHeader).assertIsDisplayed()
            onNodeWithTag(enabledRow(DashboardWidgetType.Balance)).assertIsDisplayed()
            // The list is lazy and the Available section sits under every enabled widget, of
            // which there are now enough to push it off-screen — so the row has to be scrolled
            // into existence before any selector can find it.
            onNode(hasScrollAction())
                .performScrollToNode(hasTestTag(availableRow(DashboardWidgetType.Goals)))
            onNodeWithTag(availableRow(DashboardWidgetType.Goals)).assertIsDisplayed()
            // A switched-off widget is in exactly one section, not both.
            onNodeWithTag(enabledRow(DashboardWidgetType.Goals)).assertDoesNotExist()
        }
    }

    "the loading state renders no rows" {
        runComposeUiTest {
            setContent {
                DashboardCustomizeContent(state = AsyncState.Loading, onEvent = {})
            }

            onNodeWithTag(DashboardCustomizeTestTags.EnabledHeader).assertDoesNotExist()
            onNodeWithTag(enabledRow(DashboardWidgetType.Balance)).assertDoesNotExist()
        }
    }

    "the reorder hint appears only once there are two widgets to reorder" {
        runComposeUiTest {
            setContent {
                DashboardCustomizeContent(state = AsyncState.Content(ONLY_BALANCE), onEvent = {})
            }

            onNodeWithTag(DashboardCustomizeTestTags.ReorderHint).assertDoesNotExist()
        }

        runComposeUiTest {
            setContent {
                DashboardCustomizeContent(state = AsyncState.Content(GOALS_OFF), onEvent = {})
            }

            onNodeWithTag(DashboardCustomizeTestTags.ReorderHint).assertIsDisplayed()
        }
    }

    "the switch on an on-dashboard row asks to turn that widget off" {
        runComposeUiTest {
            val events = mutableListOf<DashboardCustomizeEvent>()
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(DashboardLayoutConfig.DEFAULT),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(toggle(DashboardWidgetType.Accounts)).performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Accounts, enabled = false),
            )
        }
    }

    "tapping an available row asks to turn that widget on" {
        runComposeUiTest {
            val events = mutableListOf<DashboardCustomizeEvent>()
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(GOALS_OFF),
                    onEvent = { events += it },
                )
            }

            // The list is lazy and the Available section sits under every enabled widget, of
            // which there are now enough to push it off-screen — so the row has to be scrolled
            // into existence before any selector can find it.
            onNode(hasScrollAction())
                .performScrollToNode(hasTestTag(availableRow(DashboardWidgetType.Goals)))
            onNodeWithTag(availableRow(DashboardWidgetType.Goals)).performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Goals, enabled = true),
            )
        }
    }

    "dragging a grip upward past the row above asks to swap the two" {
        runComposeUiTest {
            val events = mutableListOf<DashboardCustomizeEvent>()
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(DashboardLayoutConfig.DEFAULT),
                    onEvent = { events += it },
                )
            }

            val rowHeight = onNodeWithTag(enabledRow(DashboardWidgetType.Accounts))
                .fetchSemanticsNode().size.height
            onNodeWithTag(dragHandle(DashboardWidgetType.Accounts)).performTouchInput {
                down(center)
                // Several moves rather than one: the gesture detector needs to clear touch slop
                // before it reports any drag at all, and the swap only fires once the dragged
                // row's midpoint has travelled into the bounds of the one above it.
                repeat(DRAG_STEPS) { moveBy(Offset(0f, -rowHeight * DRAG_OVERSHOOT / DRAG_STEPS)) }
                up()
            }
            waitForIdle()

            events shouldContain DashboardCustomizeEvent.OnWidgetMove(
                from = DashboardWidgetType.Accounts,
                to = DashboardWidgetType.SpentByCategory,
            )
        }
    }

    "an on-dashboard row shows the card style it is set to, and opens the picker" {
        runComposeUiTest {
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(
                        DashboardLayoutConfig.DEFAULT
                            .withCardStyle(DashboardWidgetType.Balance, DashboardCardStyle(variant = INLINE)),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(DashboardCustomizeTestTags.styleAction(BALANCE)).performClick()
            waitForIdle()

            onNodeWithTag(DashboardCustomizeTestTags.styleSheet(BALANCE)).assertExists()
            // The sheet opens on the style the row advertised, not on the widget's default.
            onNodeWithTag(styleOption(INLINE)).assertExists()
        }
    }

    "the style sheet offers both sizes and, for the balance card, its variants" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(item = BALANCE_ITEM, onSelect = {})
            }

            onNodeWithTag(DashboardCustomizeTestTags.styleSheet(BALANCE)).assertIsDisplayed()
            onNodeWithTag(styleOption(DashboardWidgetSize.Expanded.name)).assertIsDisplayed()
            onNodeWithTag(styleOption(DashboardWidgetSize.Compact.name)).assertIsDisplayed()
            onNodeWithTag(styleOption(SurferBalanceVariant.Minimal.name)).assertExists()
        }
    }

    "every widget renders a sample of itself in both size tiles" {
        DashboardWidgetType.entries.forEach { type ->
            runComposeUiTest {
                setContent {
                    DashboardCardStyleSheetContent(
                        item = DashboardLayoutConfig.DEFAULT.items.first { it.type == type },
                        onSelect = {},
                    )
                }

                // Each tile draws the real widget from sample data; a branch that crashes or
                // measures to nothing under the preview scaling shows up here and nowhere else.
                DashboardWidgetSize.entries.forEach { size ->
                    onNodeWithTag(DashboardCustomizeTestTags.styleOption(type.name, size.name))
                        .assertIsDisplayed()
                }
            }
        }
    }

    "a widget with no variants gets a size-only sheet" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(
                    item = DashboardLayoutConfig.DEFAULT.items.first { it.type == DashboardWidgetType.Goals },
                    onSelect = {},
                )
            }

            onNodeWithTag(
                DashboardCustomizeTestTags.styleOption(
                    DashboardWidgetType.Goals.name,
                    DashboardWidgetSize.Compact.name,
                ),
            ).assertIsDisplayed()
            onNodeWithTag(
                DashboardCustomizeTestTags.styleOption(
                    DashboardWidgetType.Goals.name,
                    SurferBalanceVariant.Classic.name,
                ),
            ).assertDoesNotExist()
        }
    }

    "picking a tile reports the whole card style, keeping the half that was not touched" {
        runComposeUiTest {
            val picked = mutableListOf<DashboardCardStyle>()
            setContent {
                DashboardCardStyleSheetContent(
                    item = BALANCE_ITEM.copy(cardStyle = DashboardCardStyle(variant = INLINE)),
                    onSelect = { picked += it },
                )
            }

            onNodeWithTag(styleOption(DashboardWidgetSize.Compact.name)).performClick()
            waitForIdle()

            picked shouldContainExactly listOf(
                DashboardCardStyle(size = DashboardWidgetSize.Compact, variant = INLINE),
            )
        }
    }

    "a drag that never leaves the row it started on asks for nothing" {
        runComposeUiTest {
            val events = mutableListOf<DashboardCustomizeEvent>()
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(DashboardLayoutConfig.DEFAULT),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(dragHandle(DashboardWidgetType.Accounts)).performTouchInput {
                down(center)
                repeat(DRAG_STEPS) { moveBy(Offset(0f, -1f)) }
                up()
            }
            waitForIdle()

            events.isEmpty() shouldBe true
        }
    }
})

/** Enough intermediate moves to clear touch slop and still land inside the row above. */
private const val DRAG_STEPS = 8

/** Drag a little further than one row so touch slop cannot eat the whole travel. */
private const val DRAG_OVERSHOOT = 1.5f

private val GOALS_OFF = DashboardLayoutConfig.DEFAULT
    .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false)

private val BALANCE = DashboardWidgetType.Balance.name

private val INLINE = SurferBalanceVariant.Inline.name

private val BALANCE_ITEM = DashboardLayoutConfig.DEFAULT.items
    .first { it.type == DashboardWidgetType.Balance }

private fun styleOption(option: String) = DashboardCustomizeTestTags.styleOption(BALANCE, option)

private val ONLY_BALANCE = DashboardLayoutConfig.DEFAULT.items
    .filter { it.type == DashboardWidgetType.Balance }
    .let { DashboardLayoutConfig(items = it) }

private fun enabledRow(type: DashboardWidgetType) = DashboardCustomizeTestTags.enabledRow(type.name)

private fun availableRow(type: DashboardWidgetType) = DashboardCustomizeTestTags.availableRow(type.name)

private fun toggle(type: DashboardWidgetType) = DashboardCustomizeTestTags.toggle(type.name)

private fun dragHandle(type: DashboardWidgetType) = DashboardCustomizeTestTags.dragHandle(type.name)
