package com.georgeci.moneysurfer.feature.account

import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_bank_url
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_bic
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_branch_phone
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_card_last4
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_description
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_iban
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AccountExtraDetailLabelTest : StringSpec({

    "well-known keys map to their localized labels" {
        AccountExtraDetailKey.IBAN.labelRes() shouldBe Res.string.account_creation_field_iban
        AccountExtraDetailKey.DESCRIPTION.labelRes() shouldBe
            Res.string.account_creation_field_description
        AccountExtraDetailKey.BIC.labelRes() shouldBe Res.string.account_creation_field_bic
        AccountExtraDetailKey.CARD_LAST4.labelRes() shouldBe
            Res.string.account_creation_field_card_last4
        AccountExtraDetailKey.BANK_URL.labelRes() shouldBe
            Res.string.account_creation_field_bank_url
        AccountExtraDetailKey.BRANCH_PHONE.labelRes() shouldBe
            Res.string.account_creation_field_branch_phone
    }

    "only IBAN and BIC use monospace rendering" {
        isMonospaceExtraDetail("IBAN") shouldBe true
        isMonospaceExtraDetail("BIC") shouldBe true
        isMonospaceExtraDetail("CARD_LAST4") shouldBe false
        isMonospaceExtraDetail("iban") shouldBe false
        isMonospaceExtraDetail("My custom field") shouldBe false
    }
})
