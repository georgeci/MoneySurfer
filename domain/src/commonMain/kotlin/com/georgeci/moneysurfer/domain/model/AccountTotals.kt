package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money

/** Sum of every account balance that shares [currencyCode]. */
data class CurrencyTotal(
    val currencyCode: CurrencyCode,
    val amount: Money,
)

/**
 * Balance totals per currency, most-used currency first (ties broken by currency code so the
 * order never depends on how the accounts happen to be sorted).
 *
 * Callers used to sum whatever matched the *first* account's currency and present that as "the"
 * total, which silently dropped every other currency. Adding across currencies is not possible
 * without FX rates, so the honest answer is one figure per currency: the head is the headline,
 * the tail has to be shown somewhere rather than swallowed.
 */
fun List<Account>.totalsByCurrency(): List<CurrencyTotal> =
    groupBy { it.currencyCode }
        .map { (currency, accounts) ->
            // Carry the bucket size through the sort — it is what "most-used" means, and
            // re-deriving it per comparison would rescan the whole list for every currency.
            CurrencyTotalWithCount(
                total = CurrencyTotal(
                    currencyCode = currency,
                    amount = accounts.fold(Money.zero()) { acc, account -> acc + account.balance },
                ),
                accountCount = accounts.size,
            )
        }
        .sortedWith(
            compareByDescending<CurrencyTotalWithCount> { it.accountCount }
                .thenBy { it.total.currencyCode.value },
        )
        .map { it.total }

private data class CurrencyTotalWithCount(
    val total: CurrencyTotal,
    val accountCount: Int,
)

/** [totalsByCurrency] rendered for display, same order — head first, everything else after it. */
fun List<Account>.formattedTotalsByCurrency(): List<String> =
    totalsByCurrency().map { MoneyFormatter.format(it.amount, it.currencyCode) }
