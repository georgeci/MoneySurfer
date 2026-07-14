package com.georgeci.moneysurfer.feature.account

import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_bank
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_card
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_cash
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_savings
import org.jetbrains.compose.resources.StringResource

internal fun AccountType.labelRes(): StringResource = when (this) {
    AccountType.CASH -> Res.string.account_type_cash
    AccountType.BANK -> Res.string.account_type_bank
    AccountType.CARD -> Res.string.account_type_card
    AccountType.SAVINGS -> Res.string.account_type_savings
}
