package com.georgeci.moneysurfer.uikit.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

enum class SurferStatusHeroTone { Primary, Secondary, Tertiary }

/**
 * Tinted hero card with a circular icon, title, and optional supporting line.
 * Generic enough to power sync status, last-backup, and similar surfaces.
 */
@Composable
fun SurferStatusHeroCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    tone: SurferStatusHeroTone = SurferStatusHeroTone.Tertiary,
) {
    val colors = AppTheme.materialColors
    val (bg, fg) = when (tone) {
        SurferStatusHeroTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        SurferStatusHeroTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        SurferStatusHeroTone.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = shape)
            .clip(shape)
            .background(bg)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = fg,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = AppTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferStatusHeroCardPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            SurferStatusHeroCard(
                title = "Up to date",
                supporting = "Last sync: today, 14:02",
                icon = SurferIcons.Sync,
                tone = SurferStatusHeroTone.Tertiary,
            )
            SurferStatusHeroCard(
                title = "Last backup · today",
                supporting = "14:02 · 18 MB · 2,341 transactions",
                icon = SurferIcons.Cloud,
                tone = SurferStatusHeroTone.Primary,
            )
        }
    }
}
