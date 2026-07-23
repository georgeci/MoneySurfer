package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

@Composable
fun SurferBalanceWidget(
    title: String,
    balance: String,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    trendText: String? = null,
    emptyText: String? = null,
) {
    val hero = size == SurferWidgetSize.Hero
    val isEmpty = emptyText != null && trendText == null
    val elevated = AppTheme.containerStyle == SurferContainerStyle.Card

    Card(
        modifier = modifier,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (hero) 20.dp else 16.dp, vertical = if (hero) 18.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (hero) 8.dp else 4.dp),
        ) {
            Text(
                text = title,
                style = if (hero) AppTheme.typography.labelLarge else AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.8f),
            )
            if (isEmpty) {
                Text(
                    text = balance,
                    style = if (hero) AppTheme.typography.displaySmall else AppTheme.typography.headlineSmall,
                    color = AppTheme.materialColors.onPrimaryContainer,
                )
            } else {
                SurferSplitAmount(
                    formattedAmount = balance,
                    tier = if (hero) SurferSplitAmountTier.Hero else SurferSplitAmountTier.Stat,
                    color = AppTheme.materialColors.onPrimaryContainer,
                    signAlpha = 0.7f,
                    fractionAlpha = 0.55f,
                )
            }
            when {
                trendText != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        // decorative — trend direction indicator; the trendText provides the accessible label
                        contentDescription = null,
                        tint = AppTheme.materialColors.onPrimaryContainer,
                        modifier = Modifier.size(if (hero) 16.dp else 14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = trendText,
                        style = if (hero) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onPrimaryContainer,
                    )
                }
                emptyText != null -> Text(
                    text = emptyText,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = "Total balance",
                balance = "€11,575.32",
                trendText = "+€412 this month",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetCompactPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = "Total balance",
                balance = "€11,575.32",
                size = SurferWidgetSize.Compact,
                trendText = "+€412",
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = "Total balance",
                balance = "—",
                emptyText = "Add your first account to see balance.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
