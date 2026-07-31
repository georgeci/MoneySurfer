package com.georgeci.moneysurfer.feature.category.screenshot

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.category.details.CategoryDetailsContent
import com.georgeci.moneysurfer.feature.category.details.CategoryDetailsState
import com.georgeci.moneysurfer.feature.category.details.CategorySubcategoryUi
import com.georgeci.moneysurfer.feature.category.details.CategoryTransactionUi
import com.georgeci.moneysurfer.feature.category.details.CategoryTrendMonthUi
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import kotlinx.datetime.YearMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of one category (issue #85).
 *
 * The trend columns scale against each other from raw minor units, so the bar heights in these
 * frames are the reference for that scaling — a change to it is visible here and nowhere else.
 * Every month is a fixed [YearMonth] in the state, so nothing about these captures depends on when
 * they run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class CategoryDetailsScreenshotTest {

    /** A parent category: trend, the split across its children, then the transactions. */
    @Test
    fun categoryDetails() = captureFullScreen("category_details") {
        CategoryDetailsContent(state = diningState(), onEvent = {})
    }

    /** A leaf has no children to break down, so the subcategory card is gone entirely. */
    @Test
    fun categoryDetailsLeaf() = captureFullScreen("category_details_leaf") {
        CategoryDetailsContent(
            state = diningState().copy(
                categoryId = CategoryId("screenshot-cat-coffee"),
                name = "Coffee",
                isLeaf = true,
                subcategories = emptyList(),
                formattedTotal = "€62.40",
                formattedAverage = "€58.10",
                formattedPerTransaction = "€4.80",
                transactionCount = 13,
            ),
            onEvent = {},
        )
    }

    /**
     * Income reads the same numbers the other way round — worth a frame for the tint alone.
     *
     * Its own months rather than the expense ones: the hero is the latest month's total and the
     * stats are derived from the same series, so borrowing Dining's trend would record a frame
     * whose hero, average and bars contradict each other.
     */
    @Test
    fun categoryDetailsIncome() = captureFullScreen("category_details_income") {
        CategoryDetailsContent(
            state = diningState().copy(
                categoryId = CategoryId("screenshot-cat-salary"),
                name = "Salary",
                type = CategoryType.INCOME,
                iconKey = "cash",
                hue = 162,
                isLeaf = true,
                subcategories = emptyList(),
                formattedTotal = "€3,200.00",
                formattedAverage = "€3,120.00",
                formattedPerTransaction = "€3,200.00",
                transactionCount = 1,
                months = listOf(
                    CategoryTrendMonthUi(YearMonth(2025, 11), 300_000, "€3,000.00"),
                    CategoryTrendMonthUi(YearMonth(2025, 12), 320_000, "€3,200.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 1), 300_000, "€3,000.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 2), 300_000, "€3,000.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 3), 302_000, "€3,020.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 4), 320_000, "€3,200.00"),
                ),
                transactions = listOf(
                    CategoryTransactionUi(
                        id = TransactionId("screenshot-tx-2"),
                        title = "April payroll",
                        formattedAmount = "€3,200.00",
                        isExpense = false,
                        categoryHueSeed = "screenshot-cat-salary",
                    ),
                ),
            ),
            onEvent = {},
        )
    }

    private fun diningState() = CategoryDetailsState.Content(
        categoryId = CategoryId("screenshot-cat-dining"),
        name = "Dining",
        type = CategoryType.EXPENSE,
        iconKey = "receipt",
        hue = 340,
        systemKind = null,
        isLeaf = false,
        formattedTotal = "€168.55",
        formattedAverage = "€196.83",
        formattedPerTransaction = "€12.04",
        transactionCount = 14,
        months = listOf(
            CategoryTrendMonthUi(YearMonth(2025, 11), 20_500, "€205.00"),
            CategoryTrendMonthUi(YearMonth(2025, 12), 19_200, "€192.00"),
            CategoryTrendMonthUi(YearMonth(2026, 1), 24_000, "€240.00"),
            CategoryTrendMonthUi(YearMonth(2026, 2), 19_800, "€198.00"),
            CategoryTrendMonthUi(YearMonth(2026, 3), 17_600, "€176.00"),
            CategoryTrendMonthUi(YearMonth(2026, 4), 16_855, "€168.55"),
        ),
        subcategories = listOf(
            CategorySubcategoryUi(
                id = CategoryId("screenshot-cat-delivery"),
                name = "Delivery",
                iconKey = "receipt",
                hue = 340,
                formattedAmount = "€106.15",
                share = 0.63f,
            ),
            CategorySubcategoryUi(
                id = CategoryId("screenshot-cat-coffee"),
                name = "Coffee",
                iconKey = "cash",
                hue = 35,
                formattedAmount = "€62.40",
                share = 0.37f,
            ),
        ),
        transactions = listOf(
            CategoryTransactionUi(
                id = TransactionId("screenshot-tx-4"),
                title = "Sushi Bar",
                formattedAmount = "€38.20",
                isExpense = true,
                categoryHueSeed = "screenshot-cat-dining",
            ),
            CategoryTransactionUi(
                id = TransactionId("screenshot-tx-5"),
                title = "Coffee",
                formattedAmount = "€4.80",
                isExpense = true,
                categoryHueSeed = "screenshot-cat-coffee",
            ),
        ),
    )
}
