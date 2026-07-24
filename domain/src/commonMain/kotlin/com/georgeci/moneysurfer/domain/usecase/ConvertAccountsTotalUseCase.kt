package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.ConvertedTotal
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.model.totalsByCurrency
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import org.koin.core.annotation.Single

/**
 * Folds every account balance into one figure in the workspace base currency, using [rates].
 *
 * Conversion happens per *currency total*, not per account: one rounding per currency instead of
 * one per row, so the headline does not drift by a cent for every extra account.
 *
 * Accounts in a currency [rates] cannot price are not dropped — they come back in
 * [ConvertedTotal.unconverted] for the caller to show beside the headline. With no rates cached
 * at all (offline first run, or the offline build, which has no provider) that degrades to
 * "base-currency accounts sum, the rest listed" rather than to a wrong number.
 */
@Single
class ConvertAccountsTotalUseCase {

    operator fun invoke(
        accounts: List<Account>,
        baseCurrency: CurrencyCode,
        rates: ExchangeRateTable?,
    ): ConvertedTotal {
        // A table quoted against some other base cannot price this workspace: only the window
        // between a base-currency change and the next refresh can produce one, and re-basing
        // second-hand quotes is a worse answer than saying "not converted".
        val usable = rates?.takeIf { it.baseCurrency == baseCurrency }
        val byCurrency = accounts.totalsByCurrency()

        var converted: Money? = null
        var usedRates = false
        val unconverted = mutableListOf<CurrencyTotal>()

        byCurrency.forEach { bucket ->
            val inBase = when {
                bucket.currencyCode == baseCurrency -> bucket.amount
                else -> usable?.convertToBase(bucket.amount, bucket.currencyCode)
                    ?.also { usedRates = true }
            }
            if (inBase == null) {
                unconverted += bucket
            } else {
                converted = (converted ?: Money.zero()) + inBase
            }
        }

        return ConvertedTotal(
            baseCurrency = baseCurrency,
            total = converted,
            unconverted = unconverted,
            // Only claim an "as of" when a quote actually moved money — a single-currency
            // workspace needs no rates and must not be labelled with yesterday's date.
            asOf = usable?.asOf?.takeIf { usedRates },
        )
    }
}
