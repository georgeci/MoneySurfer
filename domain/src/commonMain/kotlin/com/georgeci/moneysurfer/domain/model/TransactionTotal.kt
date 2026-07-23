package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType

/**
 * Sum of transaction magnitudes for one (type, currency) pair inside a date window.
 *
 * Aggregated by the database rather than by folding a loaded list, so the transactions-list
 * summary stays exact even when the list itself is paged. Split per currency because minor
 * units of different currencies must never be added together.
 */
data class TransactionTotal(
    val type: TransactionType,
    val currencyCode: CurrencyCode,
    val total: Money,
)
