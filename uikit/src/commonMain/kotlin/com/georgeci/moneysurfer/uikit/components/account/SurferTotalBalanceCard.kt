package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Aggregate "total balance" card for the manage-accounts screen. Shows a small label, an optional
 * period descriptor on the right (e.g. "EUR · April"), the hero amount, and a row of count chips
 * underneath. The active chip uses secondaryContainer; the optional archived chip uses
 * surfaceContainerHigh so archived counts read as muted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurferTotalBalanceCard(
    label: String,
    formattedTotal: String?,
    activeChipText: String,
    modifier: Modifier = Modifier,
    archivedChipText: String? = null,
    periodLabel: String? = null,
) {
    SurferCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label.uppercase(),
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
                if (periodLabel != null) {
                    Text(
                        text = periodLabel,
                        style = AppTheme.typography.labelSmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            if (formattedTotal != null) {
                SurferSplitAmount(
                    formattedAmount = formattedTotal,
                    tier = SurferSplitAmountTier.Hero,
                    color = AppTheme.materialColors.onSurface,
                    signAlpha = 0.55f,
                    fractionAlpha = 0.5f,
                )
            } else {
                Text(
                    text = "—",
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.materialColors.onSurface,
                )
            }

            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CountChip(
                    text = activeChipText,
                    background = AppTheme.materialColors.secondaryContainer,
                    contentColor = AppTheme.materialColors.onSecondaryContainer,
                    dotAlpha = 0.7f,
                )
                if (archivedChipText != null) {
                    CountChip(
                        text = archivedChipText,
                        background = AppTheme.materialColors.surfaceContainerHigh,
                        contentColor = AppTheme.materialColors.onSurfaceVariant,
                        dotAlpha = 0.55f,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountChip(
    text: String,
    background: Color,
    contentColor: Color,
    dotAlpha: Float,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = AppTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Preview
@Composable
private fun SurferTotalBalanceCardPreview() {
    SurferComponentPreview {
        SurferTotalBalanceCard(
            label = "Total balance",
            formattedTotal = "€11,575.32",
            activeChipText = "3 active",
            archivedChipText = "1 archived",
            periodLabel = "EUR · April",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun SurferTotalBalanceCardEmptyPreview() {
    SurferComponentPreview {
        SurferTotalBalanceCard(
            label = "Total balance",
            formattedTotal = null,
            activeChipText = "0 active",
            modifier = Modifier.padding(16.dp),
        )
    }
}
