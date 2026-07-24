package com.georgeci.moneysurfer.feature.account

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.model.AccountExtraDetails
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_bank_url
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_bic
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_branch_phone
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_card_last4
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_description
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_iban
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun AccountExtraDetailKey.labelRes(): StringResource = when (this) {
    AccountExtraDetailKey.IBAN -> Res.string.account_creation_field_iban
    AccountExtraDetailKey.DESCRIPTION -> Res.string.account_creation_field_description
    AccountExtraDetailKey.BIC -> Res.string.account_creation_field_bic
    AccountExtraDetailKey.CARD_LAST4 -> Res.string.account_creation_field_card_last4
    AccountExtraDetailKey.BANK_URL -> Res.string.account_creation_field_bank_url
    AccountExtraDetailKey.BRANCH_PHONE -> Res.string.account_creation_field_branch_phone
}

/**
 * Label for a stored extra-detail key: localized for the six well-known keys, and the user's own
 * wording for a custom field — which is by definition already in their language.
 */
@Composable
internal fun extraDetailLabel(key: String): String =
    AccountExtraDetails.wellKnownKey(key)?.let { stringResource(it.labelRes()) } ?: key

/** IBAN and BIC are copied character by character; a monospace face is what makes that readable. */
internal fun isMonospaceExtraDetail(key: String): Boolean =
    AccountExtraDetails.wellKnownKey(key).let {
        it == AccountExtraDetailKey.IBAN || it == AccountExtraDetailKey.BIC
    }
