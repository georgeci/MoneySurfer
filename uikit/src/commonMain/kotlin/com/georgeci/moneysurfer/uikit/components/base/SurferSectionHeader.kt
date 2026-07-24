package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Header over a group of list rows ("Active", "Archived", "Recent"), with an optional trailing
 * slot for a hint such as "Drag to reorder".
 *
 * The trailing slot is a composable rather than a `String?` because the callers that need it are
 * split between plain hint text and a tappable "See all" — the second cannot be expressed as a
 * string, and having both shapes in one signature is what kept the private copies alive.
 * [SurferSectionHeaderHint] renders the plain-text case in the matching style.
 */
@Composable
fun SurferSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Plain hint text for the [SurferSectionHeader] trailing slot. */
@Composable
fun SurferSectionHeaderHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AppTheme.typography.labelMedium,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SurferSectionHeaderPreview() {
    SurferComponentPreview {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large)) {
            SurferSectionHeader(title = "Active")
            SurferSectionHeader(
                title = "Active",
                trailing = { SurferSectionHeaderHint(text = "Drag to reorder") },
            )
        }
    }
}
