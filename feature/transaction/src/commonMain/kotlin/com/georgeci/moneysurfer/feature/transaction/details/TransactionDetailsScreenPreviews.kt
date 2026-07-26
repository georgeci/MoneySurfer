package com.georgeci.moneysurfer.feature.transaction.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.theme.AppTheme

private fun previewExpense(showDeleteConfirmation: Boolean = false, isPlanned: Boolean = false) =
    TransactionDetailsState.Content(
        transactionId = TransactionId("preview-tx-1a13"),
        formattedAmount = "−€48.20",
        type = TransactionType.EXPENSE,
        note = "Lidl — weekly shop",
        merchant = "Lidl",
        accountName = "Everyday",
        categoryName = "Groceries",
        parentCategoryName = "Home",
        tags = listOf("weekly", "food"),
        reference = "TX-1A13",
        formattedDate = "18 Mar 2025",
        isPlanned = isPlanned,
        showDeleteConfirmation = showDeleteConfirmation,
    )

@Preview
@Composable
private fun TransactionDetailsExpensePreview() {
    AppTheme {
        TransactionDetailsContent(state = previewExpense(), onEvent = {})
    }
}

@Preview
@Composable
private fun TransactionDetailsIncomePreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-2b24"),
                formattedAmount = "+€1,500.00",
                type = TransactionType.INCOME,
                note = "Monthly salary",
                merchant = "Acme Ltd",
                accountName = "Savings",
                categoryName = "Salary",
                reference = "TX-2B24",
                formattedDate = "18 Mar 2025",
                isPlanned = false,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionDetailsTransferPreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-3c35"),
                formattedAmount = "€200.00",
                type = TransactionType.EXPENSE,
                transfer = TransferLeg(fromAccountName = "Everyday", toAccountName = "Savings"),
                note = "Rainy day top-up",
                accountName = "Everyday",
                categoryName = "Transfer",
                categorySystemKind = SurferCategoryPalette.SYSTEM_KIND_TRANSFER,
                categoryIconKey = SurferCategoryPalette.TRANSFER_ICON_KEY,
                reference = "TX-3C35",
                formattedDate = "18 Mar 2025",
                isPlanned = false,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionDetailsDeletePreview() {
    AppTheme {
        TransactionDetailsContent(state = previewExpense(showDeleteConfirmation = true), onEvent = {})
    }
}

@Preview
@Composable
private fun TransactionDetailsPlannedPreview() {
    AppTheme {
        TransactionDetailsContent(state = previewExpense(isPlanned = true), onEvent = {})
    }
}
