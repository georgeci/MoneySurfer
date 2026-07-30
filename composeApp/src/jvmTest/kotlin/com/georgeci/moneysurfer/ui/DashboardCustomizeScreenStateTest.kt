package com.georgeci.moneysurfer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
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

            // Top of the list first, then scroll: the assertions below move the viewport, and
            // the enabled section's first row leaves it as soon as they do.
            onNodeWithTag(DashboardCustomizeTestTags.Root).assertIsDisplayed()
            onNodeWithTag(DashboardCustomizeTestTags.EnabledHeader).assertIsDisplayed()
            onNodeWithTag(enabledRow(DashboardWidgetType.Balance)).assertIsDisplayed()
            // A switched-off widget is in exactly one section, not both.
            onNodeWithTag(enabledRow(DashboardWidgetType.Goals)).assertDoesNotExist()
            // The Available section trails the whole enabled list, so its header needs the same
            // scroll its rows do once the dashboard carries this many widgets.
            scrollToRow(DashboardCustomizeTestTags.AvailableHeader)
            onNodeWithTag(DashboardCustomizeTestTags.AvailableHeader).assertIsDisplayed()
            scrollToRow(availableRow(DashboardWidgetType.Goals))
            onNodeWithTag(availableRow(DashboardWidgetType.Goals)).assertIsDisplayed()
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

    "the round button on an on-dashboard row asks to turn that widget off" {
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

    "the round button on an available row asks to turn that widget on" {
        runComposeUiTest {
            val events = mutableListOf<DashboardCustomizeEvent>()
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(GOALS_OFF),
                    onEvent = { events += it },
                )
            }

            scrollToRow(availableRow(DashboardWidgetType.Goals))
            onNodeWithTag(toggle(DashboardWidgetType.Goals)).performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Goals, enabled = true),
            )
        }
    }

    "an available row can be dragged too, and reports the row it lands on" {
        runComposeUiTest {
            val events = mutableListOf<DashboardCustomizeEvent>()
            setContent {
                DashboardCustomizeContent(
                    // Two widgets only, so the available row and the enabled one above it are both
                    // on screen without scrolling — the drag maths reads live viewport geometry.
                    state = AsyncState.Content(BALANCE_ON_GOALS_OFF),
                    onEvent = { events += it },
                )
            }

            val rowHeight = onNodeWithTag(availableRow(DashboardWidgetType.Goals))
                .fetchSemanticsNode().size.height
            onNodeWithTag(dragHandle(DashboardWidgetType.Goals)).performTouchInput {
                down(center)
                // Far enough up to clear the "Available" header between the sections, which is not
                // a drop target and must not swallow the drag.
                repeat(DRAG_STEPS) { moveBy(Offset(0f, -rowHeight * CROSS_SECTION_OVERSHOOT / DRAG_STEPS)) }
                up()
            }
            waitForIdle()

            // The move alone: the section a widget ends up in follows from where it was dropped,
            // which is `DashboardLayoutConfig.withWidgetMoved`'s job, not the screen's.
            events shouldContain DashboardCustomizeEvent.OnWidgetMove(
                from = DashboardWidgetType.Goals,
                to = DashboardWidgetType.Balance,
            )
        }
    }

    "on a phone the pill follows the host's card-style build key" {
        runPhoneUiTest {
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(DashboardLayoutConfig.DEFAULT),
                    onEvent = {},
                    widgetStyleEnabled = false,
                )
            }

            // Nothing to offer: card styles are switched off and a phone has no grid to widen into.
            onNodeWithTag(DashboardCustomizeTestTags.styleAction(BALANCE)).assertDoesNotExist()
        }

        runPhoneUiTest {
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(DashboardLayoutConfig.DEFAULT),
                    onEvent = {},
                    widgetStyleEnabled = true,
                )
            }

            onNodeWithTag(DashboardCustomizeTestTags.styleAction(BALANCE)).assertIsDisplayed()
        }
    }

    "at expanded width the pill is offered for the widths alone, card styles switched off or not" {
        runComposeUiTest {
            setContent {
                DashboardCustomizeContent(
                    state = AsyncState.Content(DashboardLayoutConfig.DEFAULT),
                    onEvent = {},
                    widgetStyleEnabled = false,
                )
            }

            // `host.dashboard_widget_style` ships off in both hosts, and the pill is the only way
            // into the sheet — riding the widths on that key would make them unreachable.
            onNodeWithTag(DashboardCustomizeTestTags.styleAction(BALANCE)).assertIsDisplayed()
        }
    }

    "with card styles switched off the sheet offers the widths and nothing else" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(
                    item = BALANCE_ITEM,
                    onSelect = {},
                    onSpanSelect = {},
                    styleEnabled = false,
                    spanEnabled = true,
                )
            }

            onNodeWithTag(spanOption(DashboardWidgetSpan.Half.name)).assertExists()
            // No size tiles, and no variant tiles either — both are the card-style half.
            onNodeWithTag(styleOption(DashboardWidgetSize.Compact.name)).assertDoesNotExist()
            onNodeWithTag(styleOption(SurferBalanceVariant.Minimal.name)).assertDoesNotExist()
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
                // The row above Accounts in the default layout.
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
                    widgetStyleEnabled = true,
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
                DashboardCardStyleSheetContent(item = BALANCE_ITEM, onSelect = {}, onSpanSelect = {})
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
                        onSpanSelect = {},
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

    "the balance sheet offers every treatment the widget can draw" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(item = BALANCE_ITEM, onSelect = {}, onSpanSelect = {})
            }

            // The picker's list is built from the widget's own enum, so this is the assertion that
            // the A–F set is reachable rather than just declared. `assertExists` because the row
            // scrolls: past the third tile they are composed but off-screen.
            SurferBalanceVariant.entries.forEach { variant ->
                onNodeWithTag(styleOption(variant.name)).assertExists()
            }
        }
    }

    "a widget with no variants gets a size-only sheet" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(
                    item = DashboardLayoutConfig.DEFAULT.items.first { it.type == DashboardWidgetType.Goals },
                    onSelect = {},
                    onSpanSelect = {},
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
                    onSpanSelect = {},
                )
            }

            onNodeWithTag(styleOption(DashboardWidgetSize.Compact.name)).performClick()
            waitForIdle()

            picked shouldContainExactly listOf(
                DashboardCardStyle(size = DashboardWidgetSize.Compact, variant = INLINE),
            )
        }
    }

    "the grid-width section is offered only where a grid exists to apply it" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(
                    item = BALANCE_ITEM,
                    onSelect = {},
                    onSpanSelect = {},
                    spanEnabled = false,
                )
            }

            // Sizes are always offered; widths are not, because below expanded width every widget
            // is full width whatever its span says.
            onNodeWithTag(styleOption(DashboardWidgetSize.Compact.name)).assertIsDisplayed()
            DashboardWidgetSpan.entries.forEach {
                onNodeWithTag(spanOption(it.name)).assertDoesNotExist()
            }
        }
    }

    "the grid-width section offers every span, on the one the widget already has" {
        runComposeUiTest {
            setContent {
                DashboardCardStyleSheetContent(
                    item = BALANCE_ITEM,
                    onSelect = {},
                    onSpanSelect = {},
                    spanEnabled = true,
                )
            }

            DashboardWidgetSpan.entries.forEach {
                onNodeWithTag(spanOption(it.name)).assertExists()
            }
            onNodeWithTag(spanOption(BALANCE_ITEM.span.name)).assertIsSelected()
        }
    }

    "picking a width reports the span alone, leaving the card style to its own picker" {
        runComposeUiTest {
            val picked = mutableListOf<DashboardWidgetSpan>()
            setContent {
                DashboardCardStyleSheetContent(
                    item = BALANCE_ITEM,
                    onSelect = {},
                    onSpanSelect = { picked += it },
                    spanEnabled = true,
                )
            }

            onNodeWithTag(spanOption(DashboardWidgetSpan.Half.name)).performClick()
            waitForIdle()

            picked shouldContainExactly listOf(DashboardWidgetSpan.Half)
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

/** A cross-section drag also has to travel over the section header between the two lists. */
private const val CROSS_SECTION_OVERSHOOT = 3f

/**
 * Brings a row into the viewport before addressing it.
 *
 * The sections live in one `LazyColumn`, so a row far enough down is not composed at all and no
 * selector can find it. That bit once by accident: the default layout grew past a screenful when
 * the burn-rate and insights widgets landed, and two assertions on the switched-off row started
 * failing for a reason that had nothing to do with what they test. Scrolling first keeps them
 * independent of how many widgets the default layout happens to carry.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.scrollToRow(tag: String) {
    onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
}

private val GOALS_OFF = DashboardLayoutConfig.DEFAULT
    .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false)

private val BALANCE = DashboardWidgetType.Balance.name

private val INLINE = SurferBalanceVariant.Inline.name

private val BALANCE_ITEM = DashboardLayoutConfig.DEFAULT.items
    .first { it.type == DashboardWidgetType.Balance }

private fun styleOption(option: String) = DashboardCustomizeTestTags.styleOption(BALANCE, option)

private fun spanOption(span: String) = DashboardCustomizeTestTags.spanOption(BALANCE, span)

/**
 * A phone-sized window. The default test surface is 1024 dp — expanded width — where the screen
 * offers grid widths; the cases that are about the card-style build key alone need a window with no
 * grid in it.
 */
@OptIn(ExperimentalTestApi::class)
private fun runPhoneUiTest(block: suspend SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(size = Size(PHONE_WIDTH_PX, PHONE_HEIGHT_PX), block = block)

private const val PHONE_WIDTH_PX = 411f
private const val PHONE_HEIGHT_PX = 891f

/** Balance on the dashboard, Goals under "Available" — one row per section, both on screen. */
private val BALANCE_ON_GOALS_OFF = DashboardLayoutConfig(
    items = DashboardLayoutConfig.DEFAULT.items
        .filter { it.type == DashboardWidgetType.Balance || it.type == DashboardWidgetType.Goals }
        .map { if (it.type == DashboardWidgetType.Goals) it.copy(enabled = false) else it },
)

private val ONLY_BALANCE = DashboardLayoutConfig.DEFAULT.items
    .filter { it.type == DashboardWidgetType.Balance }
    .let { DashboardLayoutConfig(items = it) }

private fun enabledRow(type: DashboardWidgetType) = DashboardCustomizeTestTags.enabledRow(type.name)

private fun availableRow(type: DashboardWidgetType) = DashboardCustomizeTestTags.availableRow(type.name)

private fun toggle(type: DashboardWidgetType) = DashboardCustomizeTestTags.toggle(type.name)

private fun dragHandle(type: DashboardWidgetType) = DashboardCustomizeTestTags.dragHandle(type.name)
