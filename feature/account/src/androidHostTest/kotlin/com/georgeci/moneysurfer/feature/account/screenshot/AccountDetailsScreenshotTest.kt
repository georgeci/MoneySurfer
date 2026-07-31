package com.georgeci.moneysurfer.feature.account.screenshot

import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.account.details.AccountBalanceChartUi
import com.georgeci.moneysurfer.feature.account.details.AccountDetailsContent
import com.georgeci.moneysurfer.feature.account.details.AccountDetailsState
import com.georgeci.moneysurfer.feature.account.details.AccountTransactionUi
import com.georgeci.moneysurfer.feature.account.details.TransactionFilter
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of one account (issue #85).
 *
 * The hero card here draws its pulsing "synced" dot only when handed a `syncedLabel`, and this
 * screen never passes one — so unlike the component gallery in `:uikit`, these frames are static.
 * See docs/testing/screenshot-tests.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class AccountDetailsScreenshotTest {

    @Test
    fun accountDetails() = captureFullScreen("account_details") {
        AccountDetailsContent(state = accountState(), onEvent = {})
    }

    /** A filter that hides most of the list — the chip row is the only thing that says why. */
    @Test
    fun accountDetailsFiltered() = captureFullScreen("account_details_filtered") {
        AccountDetailsContent(
            state = accountState().copy(filter = TransactionFilter.Income),
            onEvent = {},
        )
    }

    /** A brand-new account: flat chart, zeroed stats, nothing to list yet. */
    @Test
    fun accountDetailsEmpty() = captureFullScreen("account_details_empty") {
        AccountDetailsContent(
            state = accountState().copy(
                formattedBalance = "€0.00",
                formattedIncome = "€0.00",
                formattedExpenses = "€0.00",
                chart = AccountBalanceChartUi(
                    points = List(ChartBalances.size) { it.toFloat() to 0f },
                    formattedDelta = "€0.00",
                    isDeltaNegative = false,
                ),
                transactions = emptyList(),
                extraDetails = emptyList(),
            ),
            onEvent = {},
        )
    }

    private fun accountState() = AccountDetailsState.Content(
        accountId = AccountId("screenshot-acc-1"),
        name = "Everyday",
        formattedBalance = "€2,480.32",
        type = AccountType.BANK,
        formattedIncome = "€3,200.00",
        formattedExpenses = "€1,148.49",
        chart = AccountBalanceChartUi(
            points = ChartBalances.mapIndexed { index, balance -> index.toFloat() to balance },
            formattedDelta = "+€412.00",
            isDeltaNegative = false,
        ),
        transactions = listOf(
            AccountTransactionUi(
                id = TransactionId("screenshot-tx-1"),
                title = "Lidl — weekly shop",
                formattedAmount = "−€48.20",
                isExpense = true,
                categoryHueSeed = "screenshot-cat-1",
            ),
            AccountTransactionUi(
                id = TransactionId("screenshot-tx-2"),
                title = "March payroll",
                formattedAmount = "€3,200.00",
                isExpense = false,
                categoryHueSeed = "screenshot-cat-2",
            ),
            AccountTransactionUi(
                id = TransactionId("screenshot-tx-3"),
                title = "Coffee",
                formattedAmount = "−€3.80",
                isExpense = true,
                categoryHueSeed = "screenshot-cat-3",
            ),
        ),
        filter = TransactionFilter.All,
        extraDetails = listOf(
            AccountExtraDetail(
                key = AccountExtraDetailKey.IBAN.name,
                value = "PL61 1090 1014 0000 0712 1981 2874",
            ),
            AccountExtraDetail(key = "Broker code", value = "MS-4417"),
        ),
    )

    private companion object {

        /** A month of plausible balances, so the chart draws the shape the real series does. */
        val ChartBalances = listOf(
            2068f, 2042f, 2110f, 2095f, 2180f, 2164f, 2140f, 2210f, 2196f, 2255f,
            2231f, 2288f, 2262f, 2240f, 2310f, 2295f, 2352f, 2330f, 2308f, 2374f,
            2360f, 2412f, 2390f, 2368f, 2430f, 2415f, 2462f, 2441f, 2470f, 2480f,
        )
    }
}
