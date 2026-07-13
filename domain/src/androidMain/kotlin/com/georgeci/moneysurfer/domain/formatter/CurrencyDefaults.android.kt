package com.georgeci.moneysurfer.domain.formatter

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import java.util.Currency
import java.util.Locale

actual object CurrencyDefaults {
    actual fun systemDefault(): CurrencyCode {
        val code = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }
            .getOrNull()
            ?: FALLBACK_CURRENCY
        return CurrencyCode(code)
    }
}
