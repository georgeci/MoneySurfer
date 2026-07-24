package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Row used for fields that open a picker sheet (account, category, recurring frequency, …).
 *
 * Layout: leading squircle tile, two-line label/value column, optional supporting line, trailing
 * chevron. Wraps [SurferCard] so the row inherits the active [SurferContainerStyle] —
 * `Card` adds a 2dp shadow that lifts to 8dp when [selected], `Filled` paints
 * `surfaceContainer`, `Outlined` keeps the 1dp border. That way the picker matches every other
 * surface in the screen instead of hard-coding its own treatment.
 */
@Composable
fun SurferPickerRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    iconBackground: Color = AppTheme.materialColors.primaryContainer,
    iconTint: Color = AppTheme.materialColors.onPrimaryContainer,
    supporting: String? = null,
) {
    SurferPickerRow(
        label = label,
        value = value,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        supporting = supporting,
        leading = {
            SurferPickerRowIcon(
                icon = icon,
                background = iconBackground,
                tint = iconTint,
            )
        },
    )
}

/**
 * Slot-based variant — caller supplies the leading composable (e.g. a category bubble) instead
 * of an [ImageVector]. Use when the leading visual is more than a single tinted icon.
 */
@Composable
fun SurferPickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    supporting: String? = null,
    leading: @Composable () -> Unit,
) {
    SurferCard(modifier = modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supporting.isNullOrBlank()) {
                    Text(
                        text = supporting,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = SurferIcons.DropDown,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SurferPickerRowIcon(
    icon: ImageVector,
    background: Color,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(percent = 36))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview
@Composable
private fun SurferPickerRowCardPreview() {
    SurferComponentPreview(containerStyle = SurferContainerStyle.Card) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SurferPickerRow(
                label = "From account",
                value = "Everyday · €2,480.32",
                icon = SurferIcons.CreditCard,
                onClick = {},
            )
            SurferPickerRow(
                label = "Category",
                value = "Coffee",
                supporting = "Subcategory of Dining",
                icon = SurferIcons.Category,
                onClick = {},
            )
            SurferPickerRow(
                label = "Selected",
                value = "Savings",
                icon = SurferIcons.Savings,
                selected = true,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun SurferPickerRowFilledPreview() {
    SurferComponentPreview(containerStyle = SurferContainerStyle.Filled) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SurferPickerRow(
                label = "From account",
                value = "Everyday · €2,480.32",
                icon = SurferIcons.CreditCard,
                onClick = {},
            )
            SurferPickerRow(
                label = "Category",
                value = "Coffee",
                icon = SurferIcons.Category,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun SurferPickerRowOutlinedPreview() {
    SurferComponentPreview(containerStyle = SurferContainerStyle.Outlined) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SurferPickerRow(
                label = "From account",
                value = "Everyday · €2,480.32",
                icon = SurferIcons.CreditCard,
                onClick = {},
            )
            SurferPickerRow(
                label = "Category",
                value = "Coffee",
                icon = SurferIcons.Category,
                onClick = {},
            )
        }
    }
}
