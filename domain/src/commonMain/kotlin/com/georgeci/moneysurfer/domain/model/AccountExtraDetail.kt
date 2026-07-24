package com.georgeci.moneysurfer.domain.model

/**
 * The keys the account screens offer as one-tap chips. They are keys, not columns: the same
 * screen also lets the user name a field themselves, so the storage has to be open-ended and
 * these are simply the six spellings we pre-fill for them.
 */
enum class AccountExtraDetailKey { IBAN, DESCRIPTION, BIC, CARD_LAST4, BANK_URL, BRANCH_PHONE }

/**
 * One "Extra details" entry on an [Account].
 *
 * [key] is either the name of an [AccountExtraDetailKey] entry — in which case the UI renders a
 * localized label for it — or a label the user typed into the "Custom field…" chip, which is
 * shown verbatim. Keeping both in one string field is what lets a custom field round-trip
 * through Room and Firestore without a second schema.
 */
data class AccountExtraDetail(
    val key: String,
    val value: String,
) {
    /** The well-known key this entry stands for, or null when the user named the field. */
    val wellKnownKey: AccountExtraDetailKey?
        get() = AccountExtraDetails.wellKnownKey(key)
}

/**
 * Normalization for [Account.extraDetails].
 *
 * The list is an embedded key–value store rather than fixed columns, because the set of keys is
 * open: the creation screen ships six chips plus a "Custom field…" one, and a user-named field
 * cannot be a column. Normalizing here rather than at each call site is what keeps the invariants
 * the same whether the list came from the creation form or from another device over sync.
 *
 * Keys are compared case-insensitively so a custom field named "iban" cannot shadow the
 * well-known `IBAN` entry and produce two rows the UI would render with the same label.
 */
object AccountExtraDetails {

    /** Upper bound per account. Bounds both the stored column and the Firestore doc. */
    const val MAX_DETAILS: Int = 12

    /** Longest accepted key — a custom field name is a label, not a paragraph. */
    const val MAX_KEY_LENGTH: Int = 40

    /** Longest accepted value. Generous enough for a multi-line description. */
    const val MAX_VALUE_LENGTH: Int = 500

    private val WHITESPACE_RUN = Regex("\\s+")

    /** The [AccountExtraDetailKey] [key] names, or null when it is a user-supplied label. */
    fun wellKnownKey(key: String): AccountExtraDetailKey? =
        AccountExtraDetailKey.entries.firstOrNull { it.name == key }

    /**
     * Trims both halves, collapses whitespace runs in keys, drops entries with a blank key or a
     * blank value, removes case-insensitive duplicate keys keeping the first spelling, truncates
     * over-long keys and values, and caps the list at [MAX_DETAILS].
     *
     * Blank values are dropped rather than stored: an added-but-never-filled chip is the user
     * changing their mind, and an empty row on the details screen reads as a bug.
     */
    fun normalize(raw: List<AccountExtraDetail>): List<AccountExtraDetail> {
        val seen = mutableSetOf<String>()
        return raw.asSequence()
            .map { detail ->
                AccountExtraDetail(
                    key = detail.key.replace(WHITESPACE_RUN, " ").trim().take(MAX_KEY_LENGTH),
                    value = detail.value.trim().take(MAX_VALUE_LENGTH),
                )
            }
            .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
            .filter { seen.add(it.key.lowercase()) }
            .take(MAX_DETAILS)
            .toList()
    }
}
