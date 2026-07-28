package com.georgeci.moneysurfer.feature.transaction.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_split_title
import moneysurfer.feature.transaction.generated.resources.transaction_details_split_total
import moneysurfer.feature.transaction.generated.resources.transaction_details_split_uncategorized
import org.jetbrains.compose.resources.stringResource

/**
 * The receipt this transaction is one leg of, broken down by category.
 *
 * Rendered only for a real group (see `TransactionDetailsViewModel.resolveSplit`), and it is the
 * only place the whole receipt's figure appears: the hero above deliberately keeps showing the leg
 * the screen was opened on, because that is the row every category analytic counted. The leg on
 * screen is marked so the reader can tell which slice they arrived at.
 */
@Composable
internal fun SplitCard(split: SplitBreakdown) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.surfaceContainerHigh,
            contentColor = AppTheme.materialColors.onSurface,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            Text(
                text = stringResource(Res.string.transaction_details_split_title, split.legs.size),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            val uncategorized = stringResource(Res.string.transaction_details_split_uncategorized)
            split.legs.forEach { leg ->
                SplitLegRow(leg = leg, fallbackCategoryName = uncategorized)
            }
            HorizontalDivider(color = AppTheme.materialColors.outlineVariant)
            SplitTotalRow(formattedTotal = split.formattedTotal)
        }
    }
}

@Composable
private fun SplitLegRow(leg: SplitLegUi, fallbackCategoryName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Text(
            text = leg.categoryName.ifBlank { fallbackCategoryName },
            style = AppTheme.typography.bodyLarge,
            // The leg the screen is showing is the one the reader navigated to; the siblings are
            // context around it, so only it carries full emphasis.
            color = if (leg.isCurrent) {
                AppTheme.materialColors.onSurface
            } else {
                AppTheme.materialColors.onSurfaceVariant
            },
            fontWeight = if (leg.isCurrent) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = leg.formattedAmount,
            style = AppTheme.typography.bodyLarge,
            color = if (leg.isCurrent) {
                AppTheme.materialColors.onSurface
            } else {
                AppTheme.materialColors.onSurfaceVariant
            },
            fontWeight = if (leg.isCurrent) FontWeight.SemiBold else null,
        )
    }
}

@Composable
private fun SplitTotalRow(formattedTotal: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Text(
            text = stringResource(Res.string.transaction_details_split_total),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formattedTotal,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.materialColors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
