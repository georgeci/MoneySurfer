package com.georgeci.moneysurfer.feature.insights

import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette

/*
 * Sample state for the screen's `@Preview`s only — never referenced from a screen at runtime. Kept
 * out of the screen file so the previews read as call sites rather than as data.
 */

private val Hues = SurferCategoryPalette.hues

/** A month with spend in it, a full trend behind it and merchants named. */
internal fun previewInsightsContent(): InsightsState.Content = InsightsState.Content(
    mode = DashboardPeriod.Month,
    period = InsightsPeriodUi.Month(monthNumber = 7, year = PREVIEW_YEAR),
    canGoToNextPeriod = false,
    baseCurrency = "EUR",
    totalFormatted = "€1,842.50",
    categories = listOf(
        previewCategory("c-rent", "Rent", Hues[2], "€850.00", share = 0.46f),
        previewCategory("c-food", "Groceries", Hues[0], "€412.30", share = 0.22f),
        previewCategory("c-dine", "Dining", Hues[1], "€228.90", share = 0.12f),
        previewCategory("c-bus", "Transport", Hues.last(), "€151.30", share = 0.08f),
        previewCategory("uncategorized", null, null, "€200.00", share = 0.11f),
    ),
    months = listOf(
        previewMonth(monthNumber = 2, income = 320_000, expense = 285_000),
        previewMonth(monthNumber = 3, income = 320_000, expense = 410_000),
        previewMonth(monthNumber = 4, income = 340_000, expense = 260_000),
        previewMonth(monthNumber = 5, income = 340_000, expense = 275_000),
        previewMonth(monthNumber = 6, income = 0, expense = 190_000),
        previewMonth(monthNumber = 7, income = 340_000, expense = 184_250),
    ),
    merchants = listOf(
        InsightsMerchantUi(merchant = "Landlord", spentFormatted = "€850.00", transactionCount = 1),
        InsightsMerchantUi(merchant = "Albert Heijn", spentFormatted = "€312.10", transactionCount = 14),
        InsightsMerchantUi(merchant = "NS", spentFormatted = "€96.40", transactionCount = 8),
    ),
    hiddenCurrencies = emptyList(),
    hiddenByBaseCurrency = false,
)

/** The mixed-currency workspace: every expense filtered out, and the screen has to say so. */
internal fun previewCurrencyFilteredContent(): InsightsState.Content = previewInsightsContent().copy(
    totalFormatted = "€0.00",
    categories = emptyList(),
    merchants = emptyList(),
    hiddenCurrencies = listOf("USD", "GBP"),
    hiddenByBaseCurrency = true,
)

private fun previewCategory(
    id: String,
    name: String?,
    hue: Int?,
    spent: String,
    share: Float,
) = InsightsCategoryUi(id = id, name = name, hue = hue, spentFormatted = spent, share = share)

/** Minor units in, formatted strings derived — the previews only vary the two figures. */
private fun previewMonth(monthNumber: Int, income: Long, expense: Long) = InsightsMonthUi(
    monthNumber = monthNumber,
    year = PREVIEW_YEAR,
    income = income,
    expense = expense,
    incomeFormatted = "€${income / MINOR_PER_MAJOR}",
    expenseFormatted = "€${expense / MINOR_PER_MAJOR}",
)

private const val PREVIEW_YEAR = 2026
private const val MINOR_PER_MAJOR = 100
