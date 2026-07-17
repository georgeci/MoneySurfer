package com.georgeci.moneysurfer.uikit.components.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Single settings row: 36dp icon tile + title + optional supporting line + trailing slot.
 * Wraps content in a [SurferCard] so the row honours the active container style
 * (Filled / Outlined / Card) and re-themes when the user changes it.
 *
 * Pass [danger] for destructive rows (Log out, Delete) — title and icon switch to error color.
 * Pass [iconBackground] / [iconTint] to override the default neutral icon tile.
 */
@Composable
fun SurferSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    iconBackground: Color? = null,
    iconTint: Color? = null,
    multiline: Boolean = false,
) {
    val colors = AppTheme.materialColors
    val titleColor = if (danger) colors.error else colors.onSurface
    val effectiveIconTint = iconTint ?: if (danger) colors.error else colors.onSurfaceVariant
    val effectiveIconBg = iconBackground ?: colors.surfaceContainerHigh

    SurferCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(effectiveIconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        // decorative — icon tile identifying the setting; the row title is the accessible label
                        contentDescription = null,
                        tint = effectiveIconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                when {
                    supporting != null -> {
                        Box(modifier = Modifier.padding(top = 2.dp)) {
                            supporting()
                        }
                    }
                    supportingText != null -> Text(
                        text = supportingText,
                        style = AppTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = if (multiline) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Box(contentAlignment = Alignment.Center) { trailing() }
            }
        }
    }
}

/** Trailing chevron (right-arrow) for navigation rows. */
@Composable
fun SurferSettingsChevron(
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.materialColors.onSurfaceVariant,
) {
    Icon(
        imageVector = SurferIcons.ChevronRight,
        // decorative — navigation indicator; the row title is the accessible label
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(20.dp),
    )
}

/** Compact value pill (label + chevron) used as a trailing slot — e.g. "EUR" / "Daily". */
@Composable
fun SurferSettingsValuePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Icon(
            imageVector = SurferIcons.ChevronRight,
            // decorative — navigation indicator; the value text provides the accessible label
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Preview
@Composable
private fun SurferSettingsRowPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            SurferSettingsRow(
                icon = SurferIcons.Palette,
                title = "Appearance",
                supportingText = "Plum · Light",
                trailing = { SurferSettingsChevron() },
                onClick = {},
            )
            SurferSettingsRow(
                icon = SurferIcons.Globe,
                title = "Region & language",
                trailing = { SurferSettingsValuePill("English (UK)") },
                onClick = {},
            )
            SurferSettingsRow(
                icon = SurferIcons.Logout,
                title = "Log out",
                danger = true,
                onClick = {},
            )
        }
    }
}
