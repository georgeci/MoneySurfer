package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.datetime.number
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_full
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_today
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_yesterday
import moneysurfer.feature.transaction.generated.resources.transactions_list_months_genitive
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DateHeader(group: TransactionGroupUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = group.dateLabel.label(),
            style = AppTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = group.netFormatted,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionDateUi.label(): String = when (this) {
    TransactionDateUi.Today -> stringResource(Res.string.transactions_list_date_today)
    TransactionDateUi.Yesterday -> stringResource(Res.string.transactions_list_date_yesterday)
    // Genitive, not the pager's nominative: Russian reads "25 марта", never "25 Март".
    is TransactionDateUi.Exact -> stringResource(
        Res.string.transactions_list_date_full,
        date.day,
        stringArrayResource(Res.array.transactions_list_months_genitive)[date.month.number - 1],
        date.year,
    )
}
