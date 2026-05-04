package com.georgeci.moneysurfer.uikit.components.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Composable
fun SurferTransactionCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = AppTheme.materialColors.primary,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.surfaceVariant,
        ),
        shape = AppTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AppTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = amount,
                style = AppTheme.typography.titleMedium,
                color = amountColor,
            )
        }
    }
}

@Preview
@Composable
private fun SurferTransactionCardExpensePreview() {
    SurferComponentPreview {
        SurferTransactionCard(
            title = "Groceries",
            amount = "-$42.50",
            amountColor = Color(0xFFBA1A1A),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SurferTransactionCardIncomePreview() {
    SurferComponentPreview {
        SurferTransactionCard(
            title = "Salary",
            amount = "$1,500.00",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SurferTransactionCardNoClickPreview() {
    SurferComponentPreview {
        SurferTransactionCard(
            title = "Coffee",
            amount = "-$9.99",
            amountColor = Color(0xFFBA1A1A),
        )
    }
}
