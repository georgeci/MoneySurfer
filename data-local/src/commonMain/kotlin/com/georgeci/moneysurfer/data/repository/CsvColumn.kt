package com.georgeci.moneysurfer.data.repository

/**
 * Encoding for the entity columns that hold a small list in one `TEXT` field —
 * `budgets.categoryIds` and `transactions.tags`.
 *
 * Blank segments are dropped on both sides so a legacy `""`, a trailing comma or a stray
 * double separator decodes to a shorter list rather than to blank elements. Values that could
 * contain the separator are stripped of it before they get here (see
 * `com.georgeci.moneysurfer.domain.model.TransactionTags`); ids never contain one.
 */
internal const val CSV_SEPARATOR: String = ","

internal fun String.parseCsvColumn(): List<String> =
    if (isBlank()) emptyList() else split(CSV_SEPARATOR).filter { it.isNotBlank() }

internal fun List<String>.toCsvColumn(): String =
    filter { it.isNotBlank() }.joinToString(CSV_SEPARATOR)
