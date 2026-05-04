package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionLine
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferRecentTransactionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val isExpense: Boolean,
    val iconBgColor: Color,
    val iconFgColor: Color,
    val iconInitial: String,
    val icon: ImageVector? = null,
    val categoryHueSeed: String? = null,
    val categoryDotColor: Color? = null,
)

@Composable
fun SurferRecentTransactionsWidget(
    items: List<SurferRecentTransactionItem>,
    title: String,
    seeAllLabel: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: ((SurferRecentTransactionItem) -> Unit)? = null,
    emptyIcon: ImageVector = SurferIcons.Receipt,
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
) {
    val incomeColor = AppTheme.semanticColors.income

    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = seeAllLabel,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.clickable(onClick = onSeeAllClick),
            )
        }
        if (items.isEmpty()) {
            EmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { transaction ->
                    SurferTransactionLine(
                        icon = transaction.icon ?: SurferIcons.Receipt,
                        title = transaction.title,
                        formattedAmount = transaction.amount,
                        categoryHueSeed = transaction.categoryHueSeed,
                        meta = transaction.subtitle.takeIf { it.isNotEmpty() },
                        metaDotColor = if (transaction.categoryHueSeed.isNullOrBlank()) {
                            transaction.categoryDotColor
                        } else {
                            null
                        },
                        iconTint = transaction.iconFgColor,
                        iconBackground = transaction.iconBgColor,
                        amountColor = if (transaction.isExpense) {
                            AppTheme.materialColors.onSurface
                        } else {
                            incomeColor
                        },
                        amountPillBackground = if (transaction.isExpense) {
                            null
                        } else {
                            incomeColor.copy(alpha = 0.14f)
                        },
                        onClick = onItemClick?.let { handler -> { handler(transaction) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String?,
    subtitle: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (title != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun SurferRecentTransactionsHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferRecentTransactionsWidget(
                items = listOf(
                    SurferRecentTransactionItem(
                        id = "1",
                        title = "Lidl — weekly shop",
                        subtitle = "Groceries · Everyday · Today, 18:42",
                        amount = "−€48.20",
                        isExpense = true,
                        iconBgColor = Color(0xFFE4F4EB),
                        iconFgColor = Color(0xFF1F6A47),
                        iconInitial = "G",
                        categoryDotColor = Color(0xFF2F9970),
                    ),
                    SurferRecentTransactionItem(
                        id = "3",
                        title = "March payroll",
                        subtitle = "Salary · Emergency Fund · Yesterday",
                        amount = "+€3,200.00",
                        isExpense = false,
                        iconBgColor = Color(0xFFE4F4EB),
                        iconFgColor = Color(0xFF1F6A47),
                        iconInitial = "S",
                        categoryDotColor = Color(0xFF2F9970),
                    ),
                ),
                title = "Recent",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferRecentTransactionsEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferRecentTransactionsWidget(
                items = emptyList(),
                title = "Recent",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                emptyTitle = "No activity yet",
                emptySubtitle = "Transactions you log will show up here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
    }
}
