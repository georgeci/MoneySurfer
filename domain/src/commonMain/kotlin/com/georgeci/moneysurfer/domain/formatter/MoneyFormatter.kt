package com.georgeci.moneysurfer.domain.formatter

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money

expect object MoneyFormatter {
    fun format(money: Money, currencyCode: CurrencyCode): String
}
