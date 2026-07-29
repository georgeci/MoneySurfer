package com.georgeci.moneysurfer.feature.account.creation

import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.model.AccountExtraDetails
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AccountCreationStateTest : StringSpec({

    "a pristine blank form is invalid without showing a premature required error" {
        val state = content(name = "", nameTouched = false)

        state.nameMissing shouldBe false
        state.canSave shouldBe false
    }

    "a touched blank name shows the required error" {
        val state = content(name = "   ", nameTouched = true)

        state.nameMissing shouldBe true
        state.canSave shouldBe false
    }

    "a valid name and balance enable save" {
        val state = content(name = "Cash", balance = "125.50")

        state.balanceError.shouldBeNull()
        state.canSave shouldBe true
    }

    "a negative non-card balance disables save" {
        val state = content(name = "Cash", balance = "-1", type = AccountType.CASH)

        state.balanceError shouldBe InitialBalanceError.NEGATIVE_NOT_ALLOWED
        state.canSave shouldBe false
    }

    "a negative card balance remains valid" {
        val state = content(name = "Credit", balance = "-250", type = AccountType.CARD)

        state.balanceError.shouldBeNull()
        state.canSave shouldBe true
    }

    "currency symbol comes from the catalogue and otherwise falls back to its code" {
        val eur = CurrencyCode("EUR")
        content(
            currency = eur,
            currencies = listOf(Currency(eur, "€", "Euro")),
        ).currencySymbol shouldBe "€"

        content(currency = CurrencyCode("GBP")).currencySymbol shouldBe "GBP"
    }

    "used well-known keys disappear from the add-field choices case-insensitively" {
        val state = content(
            extraFields = listOf(
                AccountExtraField(key = "iban", value = ""),
                AccountExtraField(key = AccountExtraDetailKey.BIC.name, value = ""),
            ),
        )

        state.availableExtraFieldKeys.contains(AccountExtraDetailKey.IBAN) shouldBe false
        state.availableExtraFieldKeys.contains(AccountExtraDetailKey.BIC) shouldBe false
        state.availableExtraFieldKeys.contains(AccountExtraDetailKey.DESCRIPTION) shouldBe true
    }

    "extra fields stop being addable at the storage limit" {
        val fields = List(AccountExtraDetails.MAX_DETAILS) { index ->
            AccountExtraField(key = "field-$index", value = "")
        }

        content(extraFields = fields).canAddExtraField shouldBe false
        content(extraFields = fields.dropLast(1)).canAddExtraField shouldBe true
    }

    "editing mode is derived from the account id for loading and content states" {
        val id = accountId("editing")

        AccountCreationState.Loading(id).isEditMode shouldBe true
        content(editingAccountId = id).isEditMode shouldBe true
        content(editingAccountId = null).isEditMode shouldBe false
    }

    "an extra field resolves only canonical well-known key spellings" {
        AccountExtraField(AccountExtraDetailKey.IBAN.name, "").wellKnownKey shouldBe
            AccountExtraDetailKey.IBAN
        AccountExtraField("iban", "").wellKnownKey.shouldBeNull()
        AccountExtraField("Loyalty number", "").wellKnownKey.shouldBeNull()
    }
})

private fun content(
    name: String = "Account",
    balance: String = "",
    type: AccountType = AccountType.CASH,
    currency: CurrencyCode = CurrencyCode("EUR"),
    currencies: List<Currency> = emptyList(),
    extraFields: List<AccountExtraField> = emptyList(),
    editingAccountId: com.georgeci.moneysurfer.domain.primitives.AccountId? = null,
    nameTouched: Boolean = false,
) = AccountCreationState.Content(
    name = name,
    balance = balance,
    type = type,
    currency = currency,
    currencies = currencies,
    extraFields = extraFields,
    editingAccountId = editingAccountId,
    nameTouched = nameTouched,
)
