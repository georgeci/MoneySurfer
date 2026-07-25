package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter

/**
 * Shared fixtures for the transactions list `@Preview`s, mirroring `TransactionCreationPreviewData`
 * — the previews describe the same screen, so their scaffolding belongs in one place.
 */
/** A period with nothing in it — what both empty previews put in the summary strip. */
internal fun previewEmptySummary(): TransactionSummaryUi = TransactionSummaryUi(
    incomeFormatted = "+€0.00",
    expenseFormatted = "−€0.00",
    netFormatted = "+€0.00",
    netPositive = true,
)

internal fun previewChips(): TransactionFilterChipsUi = TransactionFilterChipsUi(
    dateRange = TransactionDateRange.FollowPeriod,
    type = TransactionTypeFilter.All,
    accountCount = 0,
    accountName = null,
    categoryCount = 0,
    categoryName = null,
    sort = TransactionSort.Newest,
)
