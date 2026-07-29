package com.georgeci.moneysurfer.feature.dashboard.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.components.SurferBottomSheetContent
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_size
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_span
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_variant
import org.jetbrains.compose.resources.stringResource

/**
 * Height every thumbnail is cropped to, so the tiles in a row line up whatever the widget's
 * natural height is. Sized to the tallest widget at [VARIANT_TILE_WIDTH]; a shorter card simply
 * leaves the rest of the tile empty rather than being blown up to fill it.
 */
private val PREVIEW_HEIGHT = 104.dp

/** Width of a variant tile. Fixed rather than shared evenly: the variant row scrolls. */
private val VARIANT_TILE_WIDTH = 150.dp

/** Width of a grid-width tile. Fixed for the same reason as [VARIANT_TILE_WIDTH]: the row scrolls. */
private val SPAN_TILE_WIDTH = 124.dp

/** Height of one cell in a grid-width tile's schematic row. */
private val SPAN_SCHEMATIC_HEIGHT = 22.dp

private val TILE_SHAPE = RoundedCornerShape(18.dp)

private val SHEET_PADDING = 20.dp

/**
 * The card-style picker for one widget. The surrounding modal is here rather than in the caller
 * because the sheet is opened from a list row, not from a navigation route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardCardStyleSheet(
    item: DashboardLayoutItem,
    onSelect: (DashboardCardStyle) -> Unit,
    onSpanSelect: (DashboardWidgetSpan) -> Unit,
    onDismiss: () -> Unit,
    styleEnabled: Boolean = true,
    spanEnabled: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        DashboardCardStyleSheetContent(
            item = item,
            onSelect = onSelect,
            onSpanSelect = onSpanSelect,
            styleEnabled = styleEnabled,
            spanEnabled = spanEnabled,
        )
    }
}

/**
 * Stateless half of the picker — public so `:composeApp` desktop UI tests can mount it directly,
 * the way [DashboardCustomizeContent] is mounted, without going through the modal's animation.
 *
 * Every style tile is a real render of the widget at the style it offers, side by side, because the
 * choice is a visual one: "Compact" and "Inline" mean nothing as words in a list.
 *
 * The two halves are gated independently, because they are governed by different things.
 * [styleEnabled] is the host's `dashboard_widget_style` build key — the card-style work is
 * unfinished and ships off. [spanEnabled] is the window: the dashboard only lays widgets out in a
 * grid from expanded width up, so on a phone a width control would change nothing visible, which
 * reads as a broken one. The caller opens this sheet when *either* is on, and a sheet with both off
 * has nothing to offer and is never opened.
 */
@Composable
fun DashboardCardStyleSheetContent(
    item: DashboardLayoutItem,
    onSelect: (DashboardCardStyle) -> Unit,
    onSpanSelect: (DashboardWidgetSpan) -> Unit,
    modifier: Modifier = Modifier,
    styleEnabled: Boolean = true,
    spanEnabled: Boolean = false,
) {
    val variants = item.type.variantOptions()
    SurferBottomSheetContent(
        title = stringResource(item.type.titleResource()),
        subtitle = stringResource(Res.string.dashboard_customize_style_subtitle),
        modifier = modifier.testTag(DashboardCustomizeTestTags.styleSheet(item.type.name)),
    ) {
        if (styleEnabled) {
            SurferSectionLabel(
                text = stringResource(Res.string.dashboard_customize_style_size),
                modifier = Modifier.padding(horizontal = SHEET_PADDING),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SHEET_PADDING),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardWidgetSize.entries.forEach { size ->
                    val style = item.cardStyle.copy(size = size)
                    StyleOptionTile(
                        label = stringResource(size.labelResource()),
                        selected = item.cardStyle.size == size,
                        tag = DashboardCustomizeTestTags.styleOption(item.type.name, size.name),
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f),
                    ) {
                        DashboardWidgetPreview(type = item.type, cardStyle = style)
                    }
                }
            }
        }

        if (styleEnabled && variants.isNotEmpty()) {
            val selectedKey = item.type.selectedVariant(item.cardStyle)?.key
            SurferSectionLabel(
                text = stringResource(Res.string.dashboard_customize_style_variant),
                modifier = Modifier.padding(top = 8.dp, start = SHEET_PADDING, end = SHEET_PADDING),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = SHEET_PADDING),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                variants.forEach { variant ->
                    val style = item.cardStyle.copy(variant = variant.key)
                    StyleOptionTile(
                        label = stringResource(variant.label),
                        selected = selectedKey == variant.key,
                        tag = DashboardCustomizeTestTags.styleOption(item.type.name, variant.key),
                        onClick = { onSelect(style) },
                        modifier = Modifier.width(VARIANT_TILE_WIDTH),
                    ) {
                        DashboardWidgetPreview(type = item.type, cardStyle = style)
                    }
                }
            }
        }

        if (spanEnabled) {
            SurferSectionLabel(
                text = stringResource(Res.string.dashboard_customize_style_span),
                modifier = Modifier.padding(top = 8.dp, start = SHEET_PADDING, end = SHEET_PADDING),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = SHEET_PADDING),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardWidgetSpan.entries.forEach { span ->
                    SpanOptionTile(
                        span = span,
                        selected = item.span == span,
                        onClick = { onSpanSelect(span) },
                        tag = DashboardCustomizeTestTags.spanOption(item.type.name, span.name),
                    )
                }
            }
        }
    }
}

/**
 * One grid width, drawn as the row it produces: [DashboardWidgetSpan.COLUMNS] cells with the ones
 * this span claims filled in. A schematic rather than a real widget render, because what the choice
 * changes is the *row* — the same card at a third of the width is a thumbnail of the neighbours it
 * would have, which is exactly what the size tiles above already show.
 */
@Composable
private fun SpanOptionTile(
    span: DashboardWidgetSpan,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.materialColors
    val outline = if (selected) colors.primary else colors.outlineVariant
    val label = stringResource(span.labelResource())
    Box(
        modifier = modifier
            .width(SPAN_TILE_WIDTH)
            .clip(TILE_SHAPE)
            .border(if (selected) 2.dp else 1.dp, outline, TILE_SHAPE),
    ) {
        Column(
            // Same reason as [StyleOptionTile]: the schematic is drawing, and the label below it is
            // already the name of the tap target laid over everything.
            modifier = Modifier
                .padding(10.dp)
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(DashboardWidgetSpan.COLUMNS) { column ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(SPAN_SCHEMATIC_HEIGHT)
                            .clip(AppTheme.shapes.extraSmall)
                            .background(
                                if (column < span.columns) {
                                    if (selected) colors.primary else colors.onSurfaceVariant
                                } else {
                                    colors.surfaceContainerHighest
                                },
                            ),
                    )
                }
            }
            Text(
                text = label,
                style = AppTheme.typography.labelLarge,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .selectable(selected = selected, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
        )
    }
}

@Composable
private fun StyleOptionTile(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val outline = if (selected) AppTheme.materialColors.primary else AppTheme.materialColors.outlineVariant
    Box(
        modifier = modifier
            .clip(TILE_SHAPE)
            .border(if (selected) 2.dp else 1.dp, outline, TILE_SHAPE),
    ) {
        Column(
            // The whole tile is drawing, not reading matter: the thumbnail's sample figures must
            // not be read out as if they were the user's, and the label below it is already the
            // name of the tap target laid over everything. Announcing both says it twice.
            modifier = Modifier
                .padding(10.dp)
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clipToBounds(),
                contentAlignment = Alignment.TopStart,
            ) {
                preview()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = AppTheme.typography.labelLarge,
                    color = if (selected) {
                        AppTheme.materialColors.onSurface
                    } else {
                        AppTheme.materialColors.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = SurferIcons.Check,
                        contentDescription = SurferSemantics.Decorative,
                        tint = AppTheme.materialColors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        // The tap target sits above the thumbnail: the sample widget carries its own clickable
        // affordances ("See all", "Add account") that would otherwise swallow the tap.
        Box(
            modifier = Modifier
                .matchParentSize()
                .selectable(selected = selected, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
        )
    }
}

@Preview
@Composable
private fun DashboardCardStyleSheetPreview() {
    SurferComponentPreview {
        DashboardCardStyleSheetContent(
            item = DashboardLayoutConfig.DEFAULT.items.first { it.type == DashboardWidgetType.Balance },
            onSelect = {},
            onSpanSelect = {},
            spanEnabled = true,
        )
    }
}
