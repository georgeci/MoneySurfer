package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_expenses
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_income
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_net
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SummaryStrip(
    summary: TransactionSummaryUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.materialColors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_income),
            value = summary.incomeFormatted,
            valueColor = AppTheme.semanticColors.income,
            modifier = Modifier.weight(1f),
        )
        SummaryDivider()
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_expenses),
            value = summary.expenseFormatted,
            valueColor = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        SummaryDivider()
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_net),
            value = summary.netFormatted,
            valueColor = if (summary.netPositive) {
                AppTheme.semanticColors.income
            } else {
                AppTheme.materialColors.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = AppTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(28.dp)
            .background(AppTheme.materialColors.outlineVariant),
    )
}
