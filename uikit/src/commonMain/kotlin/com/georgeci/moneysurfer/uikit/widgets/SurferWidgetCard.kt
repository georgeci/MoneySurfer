package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Rounded surface card with the standard widget header — a weighted [title] plus an optional
 * [trailing] slot (a "see all" link, a badge, …) — followed by [content] in the card's column.
 *
 * Shared by the widgets whose header is a single title line; widgets that also render a
 * subtitle keep their own header.
 */
@Composable
internal fun SurferWidgetCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.materialColors.surface)
            .padding(vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        content()
    }
}
