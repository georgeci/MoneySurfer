package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.datetime.LocalDate

@Preview
@Composable
private fun TransactionsByAccountPreview() {
    AppTheme {
        TransactionsByAccountContent(
            // All-accounts variant: the one where a row has to say which account it belongs to.
            state = TransactionsByAccountState.Content(
                accountId = null,
                accountName = "",
                groups = previewGroups(),
                showAccountOnRows = true,
                summary = TransactionSummaryUi(
                    incomeFormatted = PreviewPayrollAmount,
                    expenseFormatted = "−€72.70",
                    netFormatted = "+€3,127.30",
                    netPositive = true,
                ),
                query = "",
                filters = previewChips(),
                activeFilterCount = 0,
                isFiltered = false,
                periodMode = TransactionPeriodMode.Month,
                period = TransactionPeriodUi.Month(monthNumber = 3, year = 2025),
                showPeriodPager = true,
                canGoToPreviousPeriod = true,
                canGoToNextPeriod = false,
                canLoadMore = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionsByAccountEmptyPreview() {
    AppTheme {
        TransactionsByAccountContent(
            state = TransactionsByAccountState.Content(
                accountId = AccountId("preview-acc-1"),
                accountName = "Savings",
                groups = emptyList(),
                showAccountOnRows = false,
                summary = previewEmptySummary(),
                query = "",
                filters = previewChips(),
                activeFilterCount = 0,
                isFiltered = false,
                periodMode = TransactionPeriodMode.Week,
                period = TransactionPeriodUi.Week(
                    from = LocalDate(2025, 3, 25),
                    to = LocalDate(2025, 3, 31),
                    weekNumber = 13,
                    weekYear = 2025,
                ),
                showPeriodPager = true,
                canGoToPreviousPeriod = true,
                canGoToNextPeriod = true,
                canLoadMore = false,
            ),
            onEvent = {},
        )
    }
}

/** The other empty state: rows exist, the filters are hiding them, so the CTA is the way back. */
@Preview
@Composable
private fun TransactionsByAccountEmptyFilteredPreview() {
    AppTheme {
        TransactionsByAccountContent(
            state = TransactionsByAccountState.Content(
                accountId = AccountId("preview-acc-1"),
                accountName = "Savings",
                groups = emptyList(),
                showAccountOnRows = false,
                summary = previewEmptySummary(),
                query = "",
                filters = previewChips().copy(type = TransactionTypeFilter.Transfer),
                activeFilterCount = 1,
                isFiltered = true,
                periodMode = TransactionPeriodMode.Month,
                period = TransactionPeriodUi.Month(monthNumber = 3, year = 2025),
                showPeriodPager = true,
                canGoToPreviousPeriod = true,
                canGoToNextPeriod = false,
                canLoadMore = false,
            ),
            onEvent = {},
        )
    }
}

/** The payroll row's amount, which is also its day's net and the period's income. */
private const val PreviewPayrollAmount = "+€3,200.00"

/** The two days the sample rows are grouped under. */
private val PreviewToday = LocalDate(2025, 3, 26)
private val PreviewYesterday = LocalDate(2025, 3, 25)

private fun previewGroups(): List<TransactionGroupUi> = listOf(
    TransactionGroupUi(
        date = PreviewToday,
        dateLabel = TransactionDateUi.Today,
        netFormatted = "−€72.70",
        netPositive = false,
        transactions = listOf(
            TransactionRowUi(
                id = TransactionId("preview-tx-1"),
                title = "Lidl — weekly shop",
                subtitle = "Groceries",
                formattedAmount = "−€48.20",
                isExpense = true,
                categoryHueSeed = "preview-cat-1",
                accountName = "Everyday",
            ),
            TransactionRowUi(
                id = TransactionId("preview-tx-2"),
                title = "Ramen with J.",
                subtitle = "Dining",
                formattedAmount = "−€24.50",
                isExpense = true,
                categoryHueSeed = "preview-cat-2",
                accountName = "Everyday",
            ),
            TransactionRowUi(
                id = TransactionId("preview-tx-4"),
                title = "Rainy day top-up",
                subtitle = "Transfer",
                formattedAmount = "€200.00",
                isExpense = true,
                categoryHueSeed = "",
                accountName = "Savings",
                isTransfer = true,
            ),
        ),
    ),
    TransactionGroupUi(
        date = PreviewYesterday,
        dateLabel = TransactionDateUi.Yesterday,
        netFormatted = PreviewPayrollAmount,
        netPositive = true,
        transactions = listOf(
            TransactionRowUi(
                id = TransactionId("preview-tx-3"),
                title = "March payroll",
                subtitle = "Salary",
                formattedAmount = PreviewPayrollAmount,
                isExpense = false,
                categoryHueSeed = "preview-cat-3",
                accountName = "Everyday",
            ),
        ),
    ),
)
