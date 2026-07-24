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
            currency to accounts.fold(Money.zero()) { acc, account -> acc + account.balance }
        }
        .sortedWith(
            compareByDescending<Pair<CurrencyCode, Money>> { (currency, _) ->
                count { it.currencyCode == currency }
            }.thenBy { (currency, _) -> currency.value },
        )
        .map { (currency, total) -> CurrencyTotal(currency, total) }

/** [totalsByCurrency] rendered for display, same order — head first, everything else after it. */
fun List<Account>.formattedTotalsByCurrency(): List<String> =
    totalsByCurrency().map { MoneyFormatter.format(it.amount, it.currencyCode) }
