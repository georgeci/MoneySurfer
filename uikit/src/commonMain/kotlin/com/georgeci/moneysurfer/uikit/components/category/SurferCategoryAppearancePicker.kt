package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_palette_color_amber
import moneysurfer.uikit.generated.resources.uikit_palette_color_green
import moneysurfer.uikit.generated.resources.uikit_palette_color_lime
import moneysurfer.uikit.generated.resources.uikit_palette_color_magenta
import moneysurfer.uikit.generated.resources.uikit_palette_color_red
import moneysurfer.uikit.generated.resources.uikit_palette_color_rose
import moneysurfer.uikit.generated.resources.uikit_palette_color_teal
import moneysurfer.uikit.generated.resources.uikit_palette_color_violet
import moneysurfer.uikit.generated.resources.uikit_palette_icon_card
import moneysurfer.uikit.generated.resources.uikit_palette_icon_cash
import moneysurfer.uikit.generated.resources.uikit_palette_icon_category
import moneysurfer.uikit.generated.resources.uikit_palette_icon_event
import moneysurfer.uikit.generated.resources.uikit_palette_icon_receipt
import moneysurfer.uikit.generated.resources.uikit_palette_icon_savings
import moneysurfer.uikit.generated.resources.uikit_palette_icon_tag
import moneysurfer.uikit.generated.resources.uikit_palette_icon_wallet
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Grid of the palette icons, one selectable cell each.
 *
 * Cells are keyed by [SurferCategoryPalette.iconKeys] rather than by position, because the key is
 * what gets stored — the grid never has to agree with the palette on what slot three means.
 *
 * Each cell is a `selectable` with the icon's own name, so a screen reader announces "Wallet,
 * selected" instead of walking silently across eight unlabelled boxes.
 */
@Composable
fun SurferIconPickerGrid(
    selectedKey: String,
    tint: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = DEFAULT_COLUMNS,
) {
    val entries = SurferCategoryPalette.iconKeys.zip(SurferCategoryPalette.icons)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GridGap)) {
        entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                row.forEach { (key, icon) ->
                    IconCell(
                        icon = icon,
                        label = stringResource(iconLabelFor(key)),
                        selected = key == selectedKey,
                        tint = tint,
                        onClick = { onSelect(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Row of the palette tints, one selectable swatch each. */
@Composable
fun SurferColorSwatchRow(
    selectedHue: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(SwatchGap)) {
        SurferCategoryPalette.hues.zip(SurferCategoryPalette.tints).forEach { (hue, color) ->
            ColorSwatch(
                color = color,
                label = stringResource(colorLabelFor(hue)),
                selected = hue == selectedHue,
                onClick = { onSelect(hue) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IconCell(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(CellCorner))
            .background(if (selected) tint.copy(alpha = SelectedFill) else Color.Transparent)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) tint else AppTheme.materialColors.outlineVariant,
                RoundedCornerShape(CellCorner),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            tint = if (selected) tint else AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(IconSize),
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = if (selected) {
        Modifier
            .border(3.dp, AppTheme.materialColors.surface, CircleShape)
            .border(5.dp, color, CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color.copy(alpha = SwatchFill))
            .then(ring)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // The ring alone is easy to miss against a pale wash, and it is the only thing
        // distinguishing the chosen colour from the seven beside it.
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                contentDescription = SurferSemantics.Decorative,
                tint = color,
                modifier = Modifier.size(CheckSize),
            )
        }
    }
}

private const val DEFAULT_COLUMNS = 4
private const val SelectedFill = 0.15f
private const val SwatchFill = 0.18f
private val GridGap = 8.dp
private val SwatchGap = 6.dp
private val CellCorner = 14.dp
private val IconSize = 22.dp
private val CheckSize = 18.dp

/**
 * Names in palette order, not keyed by icon key or hue value — the palette lists are already the
 * single source of that ordering, and `CategoryPaletteParityTest` guards it against the domain.
 * A lookup by position cannot drift from the swatch it labels.
 */
private val IconLabels: List<StringResource> = listOf(
    Res.string.uikit_palette_icon_category,
    Res.string.uikit_palette_icon_card,
    Res.string.uikit_palette_icon_savings,
    Res.string.uikit_palette_icon_cash,
    Res.string.uikit_palette_icon_wallet,
    Res.string.uikit_palette_icon_receipt,
    Res.string.uikit_palette_icon_event,
    Res.string.uikit_palette_icon_tag,
)

private val ColorLabels: List<StringResource> = listOf(
    Res.string.uikit_palette_color_teal,
    Res.string.uikit_palette_color_amber,
    Res.string.uikit_palette_color_violet,
    Res.string.uikit_palette_color_lime,
    Res.string.uikit_palette_color_magenta,
    Res.string.uikit_palette_color_rose,
    Res.string.uikit_palette_color_red,
    Res.string.uikit_palette_color_green,
)

private fun iconLabelFor(key: String): StringResource =
    IconLabels.getOrElse(SurferCategoryPalette.iconKeys.indexOf(key)) { IconLabels.first() }

private fun colorLabelFor(hue: Int): StringResource =
    ColorLabels.getOrElse(SurferCategoryPalette.hues.indexOf(hue)) { ColorLabels.first() }

@Preview
@Composable
private fun SurferCategoryAppearancePickerPreview() {
    SurferComponentPreview {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large)) {
            SurferIconPickerGrid(
                selectedKey = SurferCategoryPalette.iconKeys[1],
                tint = SurferCategoryPalette.tints[1],
                onSelect = {},
            )
            SurferColorSwatchRow(selectedHue = SurferCategoryPalette.hues[1], onSelect = {})
        }
    }
}
