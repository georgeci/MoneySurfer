package com.georgeci.moneysurfer.feature.workspace.firstrun

import com.georgeci.moneysurfer.domain.model.Currency

/**
 * Currency codes surfaced at the top of the first-run picker. Order is intentional — the
 * most common workspace currencies for the app's audience, so the user usually doesn't
 * have to scroll or search.
 */
internal val PREFERRED_CURRENCY_CODES: List<String> =
    listOf("USD", "EUR", "GBP", "RUB", "GEL", "KZT", "UAH")

/**
 * Orders the catalogue so [PREFERRED_CURRENCY_CODES] appear first in that exact order, and
 * everything else follows sorted alphabetically by code. Pure — easy to unit test.
 */
internal fun List<Currency>.orderedForPicker(): List<Currency> {
    val rank = PREFERRED_CURRENCY_CODES.withIndex().associate { (index, code) -> code to index }
    return sortedWith(
        compareBy(
            { rank[it.code.value] ?: Int.MAX_VALUE },
            { it.code.value },
        ),
    )
}

/**
 * Case-insensitive substring filter over currency code, display name, and symbol. A blank
 * query returns the list unchanged. Pure — easy to unit test.
 */
internal fun List<Currency>.filteredBy(query: String): List<Currency> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    return filter { currency ->
        currency.code.value.lowercase().contains(q) ||
            currency.displayName.lowercase().contains(q) ||
            currency.symbol.contains(q)
    }
}
