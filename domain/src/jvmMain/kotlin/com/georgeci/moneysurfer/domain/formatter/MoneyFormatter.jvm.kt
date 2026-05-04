package com.georgeci.moneysurfer.domain.formatter

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

actual object MoneyFormatter {
    actual fun format(money: Money, currencyCode: CurrencyCode): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        formatter.currency = Currency.getInstance(currencyCode.value)
        return formatter.format(money.minor / Money.MINOR_PER_MAJOR)
    }
}
