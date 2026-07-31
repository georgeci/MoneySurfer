package com.georgeci.moneysurfer.feature.transaction.screenshot

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.transaction.creation.PreviewAccounts
import com.georgeci.moneysurfer.feature.transaction.creation.PreviewCategories
import com.georgeci.moneysurfer.feature.transaction.creation.PreviewNote
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationContent
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationState
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionEditIdentity
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionSplitLineUi
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionTypeUi
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Full-screen captures of the transaction form (issue #85).
 *
 * Taken through the stateless [TransactionCreationContent] rather than `TransactionCreationScreen`,
 * so each frame carries a fixed state instead of whatever a Koin graph would resolve. The states
 * are the ones the `@Preview` functions already describe — same sample accounts, categories and
 * note — so an IDE preview and a committed reference never drift apart.
 *
 * `TransactionCreationState.Loading` is deliberately absent: it draws an empty scaffold, which is
 * chrome the states below already carry.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class TransactionCreationScreenshotTest {

    /**
     * The date row formats [OperationTimestamp] in the *host's* zone, so a machine east of UTC+12
     * would render the following day and fail against a reference recorded on CI. Pinning the
     * default zone makes the frame the same everywhere; the JVM is the test's own, so nothing
     * outside this suite sees it.
     */
    @Before
    fun pinTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun transactionCreation() = captureFullScreen("transaction_creation") {
        TransactionCreationContent(state = expenseState(), onEvent = {})
    }

    /** The first frame of a new transaction: nothing typed, nothing picked, Save still off. */
    @Test
    fun transactionCreationEmpty() = captureFullScreen("transaction_creation_empty") {
        TransactionCreationContent(
            state = expenseState().copy(
                amount = "",
                note = "",
                selectedAccount = null,
                selectedCategory = null,
                timestamp = OperationTimestamp,
            ),
            onEvent = {},
        )
    }

    /** Income tints the amount hero and swaps the grid for the income categories. */
    @Test
    fun transactionCreationIncome() = captureFullScreen("transaction_creation_income") {
        TransactionCreationContent(
            state = expenseState().copy(
                amount = "3200",
                note = "March payroll",
                type = TransactionTypeUi.Income,
                categories = IncomeCategories,
                displayCategories = IncomeCategories,
                selectedCategory = IncomeCategories.first(),
            ),
            onEvent = {},
        )
    }

    /** Transfer replaces the hero and the category grid with the two-account block. */
    @Test
    fun transactionCreationTransfer() = captureFullScreen("transaction_creation_transfer") {
        TransactionCreationContent(
            state = expenseState().copy(
                type = TransactionTypeUi.Transfer,
                fromAccount = PreviewAccounts[0],
                toAccount = PreviewAccounts[1],
            ),
            onEvent = {},
        )
    }

    /** Accounts in different currencies: the block grows a second amount field. */
    @Test
    fun transactionCreationTransferCrossCurrency() =
        captureFullScreen("transaction_creation_transfer_cross_currency") {
            val from = PreviewAccounts[0]
            val to = PreviewAccounts[1].copy(name = "Travel USD", currencyCode = CurrencyCode("USD"))
            TransactionCreationContent(
                state = expenseState().copy(
                    amount = "250",
                    toAmount = "271.83",
                    type = TransactionTypeUi.Transfer,
                    accounts = listOf(from, to),
                    selectedAccount = from,
                    fromAccount = from,
                    toAccount = to,
                ),
                onEvent = {},
            )
        }

    /** Edit mode: identity band on top, delete action in the bar, no Transfer segment. */
    @Test
    fun transactionCreationEdit() = captureFullScreen("transaction_creation_edit") {
        val category = PreviewCategories.first()
        TransactionCreationContent(
            state = expenseState().copy(
                isEditMode = true,
                editingTransactionId = TransactionId("screenshot-tx-8213"),
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
                editingCreatedAt = Instant.fromEpochMilliseconds(OperationTimestamp),
            ),
            onEvent = {},
        )
    }

    /** The split editor open: the grid gives way to one line per category, plus the remainder. */
    @Test
    fun transactionCreationSplit() = captureFullScreen("transaction_creation_split") {
        TransactionCreationContent(
            state = expenseState().copy(
                splitLines = listOf(
                    TransactionSplitLineUi(key = 1, category = PreviewCategories[0], amount = "30"),
                    TransactionSplitLineUi(key = 2, category = PreviewCategories[3], amount = ""),
                ),
            ),
            onEvent = {},
        )
    }

    private fun expenseState() = TransactionCreationState.Content(
        amount = "48.20",
        note = PreviewNote,
        type = TransactionTypeUi.Expense,
        accounts = PreviewAccounts,
        categories = PreviewCategories,
        selectedAccount = PreviewAccounts.first(),
        selectedCategory = PreviewCategories.first(),
        isEditMode = false,
        editingTransactionId = null,
        timestamp = OperationTimestamp,
        categoryUsageCounts = emptyMap(),
        displayCategories = PreviewCategories,
    )

    private companion object {

        /**
         * 18 Mar 2025, 12:00 UTC — the afternoon the details captures are dated to, so the two
         * screens read as the same transaction. Noon rather than midnight keeps the rendered day
         * away from a zone boundary; [pinTimeZone] is what actually makes it fixed.
         */
        const val OperationTimestamp: Long = 1_742_299_200_000L

        val IncomeCategories = listOf("Salary", "Refunds", "Interest").mapIndexed { index, name ->
            Category(
                CategoryId("screenshot-income-cat-${index + 1}"),
                WorkspaceId("screenshot-ws-1"),
                name,
                CategoryType.INCOME,
                null,
                Instant.fromEpochMilliseconds(0),
            )
        }
    }
}
