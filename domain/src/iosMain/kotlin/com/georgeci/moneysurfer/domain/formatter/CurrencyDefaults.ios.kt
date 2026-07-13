package com.georgeci.moneysurfer.domain.formatter

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

actual object CurrencyDefaults {
    actual fun systemDefault(): CurrencyCode {
        val code = NSLocale.currentLocale.currencyCode?.takeIf { it.isNotBlank() }
            ?: FALLBACK_CURRENCY
        return CurrencyCode(code)
    }
}
