package com.georgeci.moneysurfer.domain.preferences

/**
 * Date window the transactions list is scoped to. Lives in the preferences package because the
 * choice is persisted alongside the other UI preferences; the window maths itself is in
 * `domain.util.TransactionPeriodWindow`.
 */
enum class TransactionPeriodMode {
    Month,
    Week,

    /** No date bounds — the list is bounded by paging instead. */
    AllTime,

    ;

    companion object {
        val DEFAULT: TransactionPeriodMode = Month
    }
}
