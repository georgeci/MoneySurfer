package com.georgeci.moneysurfer.uikit.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Circular icon medallion used as the leading element of a row — a filled container by default,
 * or an outlined ring when [outlined] is set, which is how the "add new" rows distinguish
 * themselves from real entries.
 *
 * The icon scales with the bubble rather than taking a size of its own: every hand-written copy
 * of this had picked its own pair (40/22, 36/18, 40/20) with no reason behind the difference.
 *
 * For a category bubble, whose tint comes from the palette, use `SurferCategoryBubble` instead.
 */
@Composable
fun SurferIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = DefaultSize,
    outlined: Boolean = false,
    containerColor: Color = AppTheme.materialColors.primaryContainer,
    contentColor: Color = AppTheme.materialColors.onPrimaryContainer,
) {
    val shell = if (outlined) {
        Modifier.border(1.dp, AppTheme.materialColors.outline, CircleShape)
    } else {
        Modifier.background(containerColor)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(shell),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            tint = if (outlined) AppTheme.materialColors.primary else contentColor,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

private val DefaultSize = 40.dp
private const val ICON_RATIO = 0.5f

@Preview
@Composable
private fun SurferIconBubblePreview() {
    SurferComponentPreview {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurferIconBubble(icon = SurferIcons.Wallet)
            SurferIconBubble(icon = SurferIcons.Add, outlined = true)
            SurferIconBubble(icon = SurferIcons.Savings, size = 36.dp)
        }
    }
}
