package com.georgeci.moneysurfer.feature.account.creation

import com.georgeci.moneysurfer.domain.primitives.AccountType

/**
 * Input filtering + validation for the account creation "Opening balance" field.
 * Lives outside the ViewModel so the parsing rules can be unit-tested directly.
 */
object InitialBalanceInput {

    /**
     * Reduces a raw text-field value to a parseable money string: digits, an
     * optional leading minus, and at most one decimal separator (',' normalised
     * to '.'). Letters and any extra separators are dropped, so the field can
     * never hold an unparseable amount.
     */
    fun sanitize(raw: String): String {
        val negative = raw.startsWith('-')
        val digits = StringBuilder()
        var separatorUsed = false
        for (ch in raw) {
            when {
                ch.isDigit() -> digits.append(ch)
                (ch == '.' || ch == ',') && !separatorUsed -> {
                    separatorUsed = true
                    digits.append('.')
                }
            }
        }
        return if (negative) "-$digits" else digits.toString()
    }

    /** Parses a [sanitize]d value to a [Double], or null while it is still incomplete (e.g. "" or "-"). */
    fun parse(value: String): Double? = value.toDoubleOrNull()

    /**
     * The inline error to surface for [balance] given the selected [type], or
     * null when the amount is acceptable. A negative opening balance is only
     * valid for account types that can carry debt (see [allowsNegativeBalance]).
     */
    fun errorFor(balance: String, type: AccountType): InitialBalanceError? {
        val parsed = parse(balance) ?: return null
        return if (parsed < 0 && !type.allowsNegativeBalance) {
            InitialBalanceError.NEGATIVE_NOT_ALLOWED
        } else {
            null
        }
    }
}

/** Validation failures for the opening balance field. */
enum class InitialBalanceError { NEGATIVE_NOT_ALLOWED, }

/**
 * Whether an account of this type can be created with a negative opening
 * balance. Only credit-style accounts (Card) carry debt; cash/bank/savings
 * balances start at zero or above.
 */
internal val AccountType.allowsNegativeBalance: Boolean
    get() = this == AccountType.CARD
