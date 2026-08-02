package com.georgeci.moneysurfer.feature.transaction.screenshot

import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.feature.transaction.details.SplitBreakdown
import com.georgeci.moneysurfer.feature.transaction.details.SplitLegUi
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsContent
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsState
import com.georgeci.moneysurfer.feature.transaction.details.TransferLeg
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of one transaction's details (issue #85).
 *
 * Taken through the stateless [TransactionDetailsContent] with a fixed state, like the form
 * captures next door. Every date here is already a formatted string in the state, so unlike
 * [TransactionCreationScreenshotTest] these frames carry no zone of their own.
 *
 * The variants are the ones that render a *different shape*: a transfer swaps the account and
 * category rows for From/To, a split adds a card above the details, planned adds the hero chip, and
 * the delete confirmation puts a dialog over all of it. Income is here for the hero tint alone —
 * the one place the sign of the amount changes the palette.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class TransactionDetailsScreenshotTest {

    @Test
    fun transactionDetails() = captureFullScreen("transaction_details") {
        TransactionDetailsContent(state = expenseState(), onEvent = {})
    }

    @Test
    fun transactionDetailsIncome() = captureFullScreen("transaction_details_income") {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("screenshot-tx-2b24"),
                formattedAmount = "+€3,200.00",
                type = TransactionType.INCOME,
                note = "March payroll",
                merchant = "Acme Ltd",
                accountName = "Savings",
                categoryName = "Salary",
                reference = "TX-2B24",
                formattedDate = PreviewDate,
                isPlanned = false,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }

    /** Both legs of a transfer are an ordinary expense/income pair — [TransferLeg] is the tell. */
    @Test
    fun transactionDetailsTransfer() = captureFullScreen("transaction_details_transfer") {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("screenshot-tx-3c35"),
                formattedAmount = "€200.00",
                type = TransactionType.EXPENSE,
                transfer = TransferLeg(fromAccountName = "Everyday", toAccountName = "Savings"),
                note = "Rainy day top-up",
                accountName = "Everyday",
                categoryName = "Transfer",
                categorySystemKind = SurferCategoryPalette.SYSTEM_KIND_TRANSFER,
                categoryIconKey = SurferCategoryPalette.TRANSFER_ICON_KEY,
                reference = "TX-3C35",
                formattedDate = PreviewDate,
                isPlanned = false,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }

    /** One leg of a receipt: the breakdown card marks which slice the screen was opened on. */
    @Test
    fun transactionDetailsSplit() = captureFullScreen("transaction_details_split") {
        TransactionDetailsContent(
            state = expenseState().copy(
                split = SplitBreakdown(
                    formattedTotal = "€48.20",
                    legs = listOf(
                        SplitLegUi(
                            transactionId = TransactionId("screenshot-tx-1a13"),
                            categoryName = "Groceries",
                            formattedAmount = "−€30.00",
                            isCurrent = true,
                        ),
                        SplitLegUi(
                            transactionId = TransactionId("screenshot-tx-1a14"),
                            categoryName = "Home",
                            formattedAmount = "−€18.20",
                            isCurrent = false,
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }

    /** A dated-ahead transaction: the hero says so, and nothing has hit the balance yet. */
    @Test
    fun transactionDetailsPlanned() = captureFullScreen("transaction_details_planned") {
        TransactionDetailsContent(state = expenseState().copy(isPlanned = true), onEvent = {})
    }

    @Test
    fun transactionDetailsDeleteDialog() = captureFullScreen("transaction_details_delete_dialog") {
        TransactionDetailsContent(
            state = expenseState().copy(showDeleteConfirmation = true),
            onEvent = {},
        )
    }

    private fun expenseState() = TransactionDetailsState.Content(
        transactionId = TransactionId("screenshot-tx-1a13"),
        formattedAmount = "−€48.20",
        type = TransactionType.EXPENSE,
        note = "Lidl — weekly shop",
        merchant = "Lidl",
        accountName = "Everyday",
        categoryName = "Groceries",
        parentCategoryName = "Home",
        tags = listOf("weekly", "food"),
        reference = "TX-1A13",
        formattedDate = PreviewDate,
        isPlanned = false,
        showDeleteConfirmation = false,
    )

    private companion object {
        /** The afternoon `TransactionCreationScreenshotTest` dates its form to. */
        const val PreviewDate = "18 Mar 2025"
    }
}
