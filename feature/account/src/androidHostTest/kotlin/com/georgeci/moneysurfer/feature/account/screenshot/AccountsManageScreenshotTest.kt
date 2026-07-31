package com.georgeci.moneysurfer.feature.account.screenshot

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.feature.account.manage.AccountManageUi
import com.georgeci.moneysurfer.feature.account.manage.AccountsManageContent
import com.georgeci.moneysurfer.feature.account.manage.AccountsManagePendingDelete
import com.georgeci.moneysurfer.feature.account.manage.AccountsManageState
import com.georgeci.moneysurfer.feature.account.manage.PreviewActiveAccountsEditing
import com.georgeci.moneysurfer.feature.account.manage.PreviewActiveAccountsFull
import com.georgeci.moneysurfer.feature.account.manage.PreviewArchivedAccounts
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of the account list (issue #85).
 *
 * Taken through the stateless [AccountsManageContent] with a fixed state, from the same sample
 * accounts the `@Preview` functions use, so an IDE preview and a committed reference never drift
 * apart.
 *
 * Only the delete confirmation is captured, not the archive one: both are the same
 * `SurferConfirmDialog` with different labels, and the destructive variant is the one that colours
 * its confirm button differently.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class AccountsManageScreenshotTest {

    @Test
    fun accountsManage() = captureFullScreen("accounts_manage") {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = false,
                activeAccounts = PreviewActiveAccountsFull,
                archivedAccounts = PreviewArchivedAccounts,
                formattedTotal = "€11,575.32",
            ),
            onEvent = {},
        )
    }

    /** Edit mode: every row grows a drag handle and the archived section its restore actions. */
    @Test
    fun accountsManageEditing() = captureFullScreen("accounts_manage_editing") {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = true,
                activeAccounts = PreviewActiveAccountsEditing,
                archivedAccounts = PreviewArchivedAccounts,
                formattedTotal = "€11,395.32",
            ),
            onEvent = {},
        )
    }

    /**
     * Accounts in more than one currency: the minor totals sit beside the headline, unconverted.
     *
     * The two extra accounts are what those totals are *of* — the screen derives the row from the
     * accounts it lists, so a frame with a dollar total and no dollar account would record a state
     * the screen cannot reach.
     */
    @Test
    fun accountsManageMultiCurrency() = captureFullScreen("accounts_manage_multi_currency") {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = false,
                activeAccounts = PreviewActiveAccountsFull + listOf(
                    AccountManageUi(
                        id = AccountId("screenshot-acc-usd"),
                        name = "Travel USD",
                        type = AccountType.CARD,
                        formattedBalance = "$1,240.00",
                        currency = "USD",
                    ),
                    AccountManageUi(
                        id = AccountId("screenshot-acc-gbp"),
                        name = "London rent",
                        type = AccountType.BANK,
                        formattedBalance = "£310.50",
                        currency = "GBP",
                    ),
                ),
                archivedAccounts = emptyList(),
                formattedTotal = "€11,575.32",
                otherCurrencyTotals = listOf("$1,240.00", "£310.50"),
            ),
            onEvent = {},
        )
    }

    /** What a fresh workspace shows before the first account exists. */
    @Test
    fun accountsManageEmpty() = captureFullScreen("accounts_manage_empty") {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = false,
                activeAccounts = emptyList(),
                archivedAccounts = emptyList(),
                formattedTotal = null,
            ),
            onEvent = {},
        )
    }

    @Test
    fun accountsManageDeleteDialog() = captureFullScreen("accounts_manage_delete_dialog") {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = true,
                activeAccounts = PreviewActiveAccountsFull,
                archivedAccounts = emptyList(),
                formattedTotal = "€11,575.32",
                pendingDelete = AccountsManagePendingDelete(
                    id = AccountId("preview-acc-3"),
                    name = "Cash wallet",
                ),
            ),
            onEvent = {},
        )
    }
}
