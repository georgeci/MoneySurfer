package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Composable
fun SurferDetailPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = SurferIcons.Info,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.materialColors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            modifier = Modifier.padding(AppTheme.spacing.large),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = text,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun SurferDetailPlaceholderPreview() {
    SurferComponentPreview {
        SurferDetailPlaceholder(text = "Select an item to see details")
    }
}
