package com.georgeci.moneysurfer.domain.model

/**
 * Normalization for [Transaction.tags].
 *
 * Tags are an embedded list, not a join table: they are free-form labels with no identity
 * of their own — nothing renames, merges or counts them across transactions yet. A single
 * column costs one migration now; a join table would cost one anyway once a tag manager
 * exists, and buys nothing before then.
 *
 * That storage choice is why normalization is mandatory rather than cosmetic. Room keeps the
 * list as one CSV column, so a comma inside a tag would silently split it in two on the next
 * read. Stripping the separator is the only round-trip-safe option — rejecting the input
 * would surface a storage detail in the UI, and escaping would make the column unreadable
 * in a SQL browser for no gain.
 */
object TransactionTags {

    /**
     * Upper bound on tags per transaction. Bounds both the CSV column and the Firestore doc,
     * and no design surface shows more than a handful.
     */
    const val MAX_TAGS: Int = 10

    private const val SEPARATOR = ','
    private val WHITESPACE_RUN = Regex("\\s+")

    /**
     * Strips separators, trims, drops blanks, removes case-insensitive duplicates keeping the
     * first spelling the user typed, and caps at [MAX_TAGS].
     */
    fun normalize(raw: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return raw.asSequence()
            .map { it.replace(SEPARATOR, ' ').replace(WHITESPACE_RUN, " ").trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
            .take(MAX_TAGS)
            .toList()
    }
}
