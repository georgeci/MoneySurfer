package com.georgeci.moneysurfer.feature.transaction.creation

/**
 * Pure validation for the transaction amount fields (single amount and both
 * transfer legs). Lives outside the ViewModel so the parsing rules can be
 * unit-tested directly — same split as `InitialBalanceInput` in feature/account.
 */
object TransactionAmountInput {

    /** Digits with at most one decimal separator; no sign, no exponent. */
    private val AMOUNT_PATTERN = Regex("""\d*\.?\d*""")

    /**
     * Parses a raw amount (',' normalised to '.') to a [Double], or null when
     * the text is not a plain unsigned decimal — e.g. "1.2.3", "12a", "-5".
     */
    fun parse(amount: String): Double? {
        val normalized = amount.replace(',', '.')
        if (!normalized.matches(AMOUNT_PATTERN)) return null
        return normalized.toDoubleOrNull()
    }

    /** Submit gate: the amount parses and is strictly positive. */
    fun isValid(amount: String): Boolean = (parse(amount) ?: return false) > 0.0

    /**
     * The inline error to surface for [amount], or null when the field is
     * empty (nothing to complain about yet) or holds a positive amount.
     */
    fun errorFor(amount: String): TransactionAmountError? {
        if (amount.isBlank()) return null
        val parsed = parse(amount) ?: return TransactionAmountError.INVALID_FORMAT
        return if (parsed > 0.0) null else TransactionAmountError.NOT_POSITIVE
    }
}

/** Validation failures for a transaction amount field. */
enum class TransactionAmountError { INVALID_FORMAT, NOT_POSITIVE }
