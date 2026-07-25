package com.georgeci.moneysurfer.feature.dashboard.customize

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
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.components.SurferBottomSheetContent
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_size
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
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        DashboardCardStyleSheetContent(item = item, onSelect = onSelect)
    }
}

/**
 * Stateless half of the picker — public so `:composeApp` desktop UI tests can mount it directly,
 * the way [DashboardCustomizeContent] is mounted, without going through the modal's animation.
 *
 * Every tile is a real render of the widget at the style it offers, side by side, because the
 * choice is a visual one: "Compact" and "Inline" mean nothing as words in a list.
 */
@Composable
fun DashboardCardStyleSheetContent(
    item: DashboardLayoutItem,
    onSelect: (DashboardCardStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val variants = item.type.variantOptions()
    SurferBottomSheetContent(
        title = stringResource(item.type.titleResource()),
        subtitle = stringResource(Res.string.dashboard_customize_style_subtitle),
        modifier = modifier.testTag(DashboardCustomizeTestTags.styleSheet(item.type.name)),
    ) {
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

        if (variants.isNotEmpty()) {
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
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clipToBounds()
                    // Sample figures a screen reader must not read out as if they were the user's.
                    .clearAndSetSemantics {},
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
        )
    }
}
