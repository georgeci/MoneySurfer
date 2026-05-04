package com.georgeci.moneysurfer.uikit.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Section wrapper for a stack of [SurferSettingsRow]s. Optional [title] is rendered above the
 * first row in the primary tint; optional [footnote] sits below the last row in the variant tint.
 *
 * Default horizontal padding (16dp) matches the design's hub layout. Row gap (8dp) is applied
 * automatically — children should be plain rows, not pre-stacked containers.
 */
@Composable
fun SurferSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footnote: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    rowSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(bottom = 12.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
            content()
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSettingsGroupPreview() {
    SurferComponentPreview {
        Column {
            SurferSettingsGroup(title = "Personalization") {
                SurferSettingsRow(
                    icon = SurferIcons.Palette,
                    title = "Appearance",
                    supportingText = "Plum · Light",
                    trailing = { SurferSettingsChevron() },
                    onClick = {},
                )
                SurferSettingsRow(
                    icon = SurferIcons.Notifications,
                    title = "Notifications",
                    trailing = { SurferSettingsSwitch(checked = true, onCheckedChange = {}) },
                )
            }
            SurferSettingsGroup(
                title = "Color source",
                footnote = "Use a fixed accent, or match your wallpaper automatically.",
            ) {
                SurferSettingsRow(
                    icon = SurferIcons.Sparkle,
                    title = "Dynamic colors",
                    supportingText = "Match wallpaper palette",
                    trailing = { SurferSettingsSwitch(checked = false, onCheckedChange = {}) },
                )
            }
        }
    }
}
