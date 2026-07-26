package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Preview
@Composable
private fun TransactionCreationFilledPreview() {
    AppTheme {
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "48.20",
                note = PreviewNote,
                type = TransactionTypeUi.Expense,
                accounts = PreviewAccounts,
                categories = PreviewCategories,
                selectedAccount = PreviewAccounts.first(),
                selectedCategory = PreviewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
            ),
            onEvent = {},
        )
    }
}

/** Edit mode: identity band on top, delete action in the bar, no Transfer segment. */
@Preview
@Composable
private fun TransactionCreationEditPreview() {
    AppTheme {
        val category = PreviewCategories.first()
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "48.20",
                note = PreviewNote,
                type = TransactionTypeUi.Expense,
                accounts = PreviewAccounts,
                categories = PreviewCategories,
                selectedAccount = PreviewAccounts.first(),
                selectedCategory = category,
                isEditMode = true,
                editingTransactionId = TransactionId("preview-tx-8213"),
                editIdentity = TransactionEditIdentity(
                    reference = "TX-8213",
                    type = TransactionTypeUi.Expense,
                    note = PreviewNote,
                    formattedAmount = "−€48.20",
                    categoryId = category.id.value,
                    categoryIconKey = category.iconKey,
                    categoryHue = category.hue,
                    categorySystemKind = null,
                ),
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionCreationTransferPreview() {
    AppTheme {
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "48.20",
                note = PreviewNote,
                type = TransactionTypeUi.Transfer,
                accounts = PreviewAccounts,
                categories = PreviewCategories,
                selectedAccount = PreviewAccounts.first(),
                selectedCategory = PreviewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
                fromAccount = PreviewAccounts[0],
                toAccount = PreviewAccounts[1],
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionCreationTransferCrossCurrencyPreview() {
    AppTheme {
        val from = PreviewAccounts[0]
        val to = PreviewAccounts[1].copy(
            name = "Travel USD",
            currencyCode = CurrencyCode("USD"),
        )
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "250",
                toAmount = "271.83",
                note = PreviewNote,
                type = TransactionTypeUi.Transfer,
                accounts = listOf(from, to),
                categories = PreviewCategories,
                selectedAccount = from,
                selectedCategory = PreviewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
                fromAccount = from,
                toAccount = to,
            ),
            onEvent = {},
        )
    }
}
