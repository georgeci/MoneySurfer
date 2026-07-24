package com.georgeci.moneysurfer.feature.account

import androidx.compose.ui.graphics.vector.ImageVector
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_bank
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_card
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_cash
import com.georgeci.moneysurfer.feature.account.generated.resources.account_type_savings
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.jetbrains.compose.resources.StringResource

internal fun AccountType.labelRes(): StringResource = when (this) {
    AccountType.CASH -> Res.string.account_type_cash
    AccountType.BANK -> Res.string.account_type_bank
    AccountType.CARD -> Res.string.account_type_card
    AccountType.SAVINGS -> Res.string.account_type_savings
}

/**
 * Glyph for an account type. Lives beside [labelRes] rather than in uikit: the mapping is keyed
 * by a domain enum, and uikit deliberately knows nothing about domain.
 */
internal fun AccountType.icon(): ImageVector = when (this) {
    AccountType.CASH -> SurferIcons.Cash
    AccountType.BANK -> SurferIcons.Bank
    AccountType.CARD -> SurferIcons.CreditCard
    AccountType.SAVINGS -> SurferIcons.Savings
}
