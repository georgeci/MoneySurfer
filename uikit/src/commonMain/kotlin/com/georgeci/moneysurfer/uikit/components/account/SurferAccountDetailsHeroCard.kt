package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Hero card on the Account Details screen — primary-container tonal block with the account
 * identity (icon + name + sub-line), the headline balance, and a pulsing live-sync indicator.
 *
 * Built on M3 [Card] so the surface still picks up tonal-elevation overlays from the theme.
 * Mirrors the `'hero'` variant from the Accounts.html design spec.
 */
@Composable
fun SurferAccountDetailsHeroCard(
    icon: ImageVector,
    name: String,
    subtitle: String,
    formattedBalance: String,
    syncedLabel: String,
    modifier: Modifier = Modifier,
) {
    val elevated = AppTheme.containerStyle == SurferContainerStyle.Card
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.primaryContainer,
            contentColor = AppTheme.materialColors.onPrimaryContainer,
        ),
        elevation = if (elevated) {
            CardDefaults.elevatedCardElevation()
        } else {
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                draggedElevation = 0.dp,
                disabledElevation = 0.dp,
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.14f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AppTheme.materialColors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (name.isNotBlank()) {
                        Text(
                            text = name,
                            style = AppTheme.typography.titleMedium,
                            color = AppTheme.materialColors.onPrimaryContainer,
                            maxLines = 1,
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = AppTheme.typography.labelMedium.copy(letterSpacing = 0.4.sp),
                            color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            SurferSplitAmount(
                formattedAmount = formattedBalance.ifBlank { "0.00" },
                tier = SurferSplitAmountTier.Hero,
                color = AppTheme.materialColors.onPrimaryContainer,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PulsingDot(color = AppTheme.semanticColors.income)
                Text(
                    text = syncedLabel,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "sync-dot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sync-dot-scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sync-dot-alpha",
    )
    Box(
        modifier = Modifier.size(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp * scale)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha)),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Preview
@Composable
private fun SurferAccountDetailsHeroCardPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            SurferAccountDetailsHeroCard(
                icon = SurferIcons.Wallet,
                name = "Everyday",
                subtitle = "EUR",
                formattedBalance = "€2,480.32",
                syncedLabel = "Synced 2 min ago · Tap for full history",
            )
            SurferAccountDetailsHeroCard(
                icon = SurferIcons.Savings,
                name = "Emergency Fund",
                subtitle = "EUR",
                formattedBalance = "€8,915.00",
                syncedLabel = "Synced just now · Tap for full history",
            )
        }
    }
}
