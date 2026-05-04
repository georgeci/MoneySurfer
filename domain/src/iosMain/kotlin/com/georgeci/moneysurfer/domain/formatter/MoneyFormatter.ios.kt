package com.georgeci.moneysurfer.domain.formatter

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

actual object MoneyFormatter {
    actual fun format(money: Money, currencyCode: CurrencyCode): String {
        val formatter = NSNumberFormatter()
        formatter.numberStyle = NSNumberFormatterCurrencyStyle
        formatter.locale = NSLocale.currentLocale
        formatter.currencyCode = currencyCode.value
        val value = NSNumber(double = money.minor / Money.MINOR_PER_MAJOR)
        return formatter.stringFromNumber(value) ?: "${currencyCode.value} ${money.minor / Money.MINOR_PER_MAJOR}"
    }
}
