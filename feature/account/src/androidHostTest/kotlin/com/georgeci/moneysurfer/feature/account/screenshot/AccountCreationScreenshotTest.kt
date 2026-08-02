package com.georgeci.moneysurfer.feature.account.screenshot

import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.feature.account.creation.AccountCreationContent
import com.georgeci.moneysurfer.feature.account.creation.AccountCreationState
import com.georgeci.moneysurfer.feature.account.creation.AccountExtraField
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of the account form (issue #85).
 *
 * `firstRun` is a parameter of the content rather than part of its state, and it changes the
 * chrome — the title, and whether there is a way back at all — so it earns a frame of its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class AccountCreationScreenshotTest {

    @Test
    fun accountCreation() = captureFullScreen("account_creation") {
        AccountCreationContent(state = filledState(), firstRun = false, onEvent = {})
    }

    /** The first frame of a new account: name empty, no extra details, every chip still offered. */
    @Test
    fun accountCreationEmpty() = captureFullScreen("account_creation_empty") {
        AccountCreationContent(
            state = filledState().copy(name = "", balance = "", extraFields = emptyList()),
            firstRun = false,
            onEvent = {},
        )
    }

    /** The launch step: a different title and no back affordance — there is nowhere to go yet. */
    @Test
    fun accountCreationFirstRun() = captureFullScreen("account_creation_first_run") {
        AccountCreationContent(
            state = filledState().copy(name = "", balance = "", extraFields = emptyList()),
            firstRun = true,
            onEvent = {},
        )
    }

    /** Editing an existing account: same form, "Save changes" chrome. */
    @Test
    fun accountCreationEdit() = captureFullScreen("account_creation_edit") {
        AccountCreationContent(
            state = filledState().copy(editingAccountId = AccountId("screenshot-acc-1")),
            firstRun = false,
            onEvent = {},
        )
    }

    /**
     * Both inline validations at once: a name that was edited and left blank, and a negative
     * opening balance on a type that cannot carry debt. The pristine form above shows neither,
     * which is the point of `nameTouched`.
     */
    @Test
    fun accountCreationErrors() = captureFullScreen("account_creation_errors") {
        AccountCreationContent(
            state = filledState().copy(name = "", nameTouched = true, balance = "-120.00"),
            firstRun = false,
            onEvent = {},
        )
    }

    private fun filledState() = AccountCreationState.Content(
        name = "Emergency fund",
        balance = "2,480.00",
        type = AccountType.BANK,
        currency = CurrencyCode("EUR"),
        currencies = listOf(
            Currency(CurrencyCode("EUR"), symbol = "€", displayName = "Euro"),
            Currency(CurrencyCode("USD"), symbol = "$", displayName = "US Dollar"),
            Currency(CurrencyCode("GBP"), symbol = "£", displayName = "British Pound"),
            Currency(CurrencyCode("PLN"), symbol = "zł", displayName = "Polish Zloty"),
        ),
        extraFields = listOf(
            AccountExtraField(
                key = AccountExtraDetailKey.IBAN.name,
                value = "PL61 1090 1014 0000 0712 1981 2874",
            ),
            AccountExtraField(
                key = AccountExtraDetailKey.DESCRIPTION.name,
                value = "Joint emergency reserve — 6 months of expenses.",
            ),
            AccountExtraField(key = "Broker code", value = "MS-4417"),
        ),
        editingAccountId = null,
    )
}
