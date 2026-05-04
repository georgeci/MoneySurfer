package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Composable
fun SurferFullScreenLoader(
    modifier: Modifier = Modifier,
    scrimColor: Color = AppTheme.materialColors.scrim.copy(alpha = 0.32f),
) {
    Box(
        modifier = modifier
            .background(scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = AppTheme.shapes.large,
            color = AppTheme.materialColors.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = AppTheme.materialColors.primary,
                    trackColor = AppTheme.materialColors.surfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferFullScreenLoaderPreview() {
    SurferComponentPreview {
        SurferFullScreenLoader(modifier = Modifier.size(width = 240.dp, height = 360.dp))
    }
}
