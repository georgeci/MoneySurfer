package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType

/**
 * Effect of a transaction on account balances. Pure, no IO.
 *
 * Only ACTUAL transactions contribute. PLANNED rows return [emptyMap]; deleted rows
 * are absent from the source data and never reach this function.
 *
 * Sign comes from [TransactionType]; we use [Money.abs] so callers passing a signed
 * `money` (legacy convention where expense was a negative Money) still get the right
 * impact. New code should write positive [Money] and rely on [type] for the sign.
 */
fun Transaction.balanceImpact(): Map<AccountId, Money> {
    if (status != TransactionStatus.ACTUAL) return emptyMap()
    val magnitude = money.abs()
    val signed = when (type) {
        TransactionType.INCOME -> magnitude
        TransactionType.OPENING_BALANCE -> magnitude
        TransactionType.EXPENSE -> -magnitude
    }
    return mapOf(accountId to signed)
}

/**
 * Net delta to apply to account balances when a transaction transitions from [old] to [new].
 *
 * Both arguments are nullable:
 * - `old == null && new != null` → create
 * - `old != null && new != null` → update
 * - `old != null && new == null` → delete (the row was hard-deleted locally)
 * - `old == null && new == null` → no-op (idempotent at the boundary)
 *
 * The result drops zero entries so callers never apply a no-op increment.
 */
fun calculateBalanceDelta(
    old: Transaction?,
    new: Transaction?,
): Map<AccountId, Money> {
    val a = old?.balanceImpact().orEmpty()
    val b = new?.balanceImpact().orEmpty()
    if (a.isEmpty() && b.isEmpty()) return emptyMap()
    val keys = a.keys + b.keys
    val result = mutableMapOf<AccountId, Money>()
    for (key in keys) {
        val before = a[key] ?: Money.zero()
        val after = b[key] ?: Money.zero()
        val diff = after - before
        if (!diff.isZero()) result[key] = diff
    }
    return result
}
