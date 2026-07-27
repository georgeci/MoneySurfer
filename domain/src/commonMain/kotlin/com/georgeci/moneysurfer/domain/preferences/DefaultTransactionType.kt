package com.georgeci.moneysurfer.domain.preferences

/**
 * Which kind of transaction the creation screen should open on.
 *
 * Deliberately narrower than
 * [TransactionType][com.georgeci.moneysurfer.domain.primitives.TransactionType]: `OPENING_BALANCE`
 * is written by account creation and is never something the user starts, and a transfer is gated
 * by the `host.transfer_enabled` capability — offering it here would let the picker store a
 * default the host cannot open.
 */
enum class DefaultTransactionType {
    Expense,
    Income,

    ;

    companion object {
        val DEFAULT: DefaultTransactionType = Expense
    }
}
