package com.georgeci.moneysurfer.feature.account

import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_bank
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_card
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_cash
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_savings
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AccountTypeLabelTest : StringSpec({

    "each account type maps to its own string resource" {
        AccountType.CASH.labelRes() shouldBe Res.string.account_type_cash
        AccountType.BANK.labelRes() shouldBe Res.string.account_type_bank
        AccountType.CARD.labelRes() shouldBe Res.string.account_type_card
        AccountType.SAVINGS.labelRes() shouldBe Res.string.account_type_savings
    }

    "all account types resolve to distinct resources" {
        val resources = AccountType.entries.map { it.labelRes() }
        resources.toSet().size shouldBe AccountType.entries.size
    }

    "each account type maps to its semantic icon" {
        AccountType.CASH.icon() shouldBe SurferIcons.Cash
        AccountType.BANK.icon() shouldBe SurferIcons.Bank
        AccountType.CARD.icon() shouldBe SurferIcons.CreditCard
        AccountType.SAVINGS.icon() shouldBe SurferIcons.Savings
    }
})
