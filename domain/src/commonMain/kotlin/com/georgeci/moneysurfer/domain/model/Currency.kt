package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode

data class Currency(
    val code: CurrencyCode,
    val symbol: String,
    val displayName: String,
)
