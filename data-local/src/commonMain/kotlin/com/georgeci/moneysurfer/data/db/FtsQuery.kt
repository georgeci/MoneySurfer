package com.georgeci.moneysurfer.data.db

/**
 * Turns free-form user input into an FTS4 MATCH expression.
 *
 * Raw input must never reach `MATCH`: unbalanced quotes are a syntax error (SQLite
 * throws, the Flow dies), and bare `OR` / `NOT` / `NEAR` are operators, not words.
 * Everything that is not a letter or a digit is therefore treated as a separator, and
 * each surviving token gets a `*` so typing progressively narrows results instead of
 * matching nothing until the word is complete — the trailing `*` also demotes operator
 * keywords back to ordinary terms.
 *
 * Tokens are space-joined, i.e. implicitly AND-ed by FTS4.
 */
object FtsQuery {

    /** Returns null when [raw] carries no searchable token — callers should skip the query. */
    fun fromUserInput(raw: String): String? {
        val tokens = raw
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString(separator = "")
            .split(' ')
            .filter { it.isNotEmpty() }
        return if (tokens.isEmpty()) null else tokens.joinToString(separator = " ") { "$it*" }
    }
}
