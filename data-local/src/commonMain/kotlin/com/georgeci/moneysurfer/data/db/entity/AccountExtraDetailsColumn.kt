package com.georgeci.moneysurfer.data.db.entity

import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.AccountExtraDetails
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Codec for [AccountEntity.extraDetails].
 *
 * JSON rather than the CSV used for transaction tags: an entry is a pair, and a separator-based
 * encoding of pairs would have to escape two characters instead of stripping one — at which point
 * the column stops being readable in a SQL browser anyway, which was CSV's only advantage.
 *
 * Built on the `JsonElement` API rather than a `@Serializable` holder because this module does not
 * apply the serialization compiler plugin, and because the column is a stored format: spelling the
 * two field names out here makes it obvious that renaming them is a migration, not a refactor.
 *
 * [encode] normalizes, so nothing that violates the [AccountExtraDetails] invariants can reach the
 * database — including documents pulled from a client we do not control. [decode] is deliberately
 * lenient: a column we cannot parse degrades to "this account has no extra details" rather than
 * failing every read of the accounts table.
 */
object AccountExtraDetailsColumn {

    /** Value written for an account with no details — also the column default in the schema. */
    const val EMPTY: String = "[]"

    private const val FIELD_KEY = "key"
    private const val FIELD_VALUE = "value"

    fun encode(details: List<AccountExtraDetail>): String {
        val normalized = AccountExtraDetails.normalize(details)
        if (normalized.isEmpty()) return EMPTY
        return buildJsonArray {
            normalized.forEach { detail ->
                add(
                    buildJsonObject {
                        put(FIELD_KEY, detail.key)
                        put(FIELD_VALUE, detail.value)
                    },
                )
            }
        }.toString()
    }

    fun decode(raw: String): List<AccountExtraDetail> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val key = obj[FIELD_KEY]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val value = obj[FIELD_VALUE]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                AccountExtraDetail(key = key, value = value)
            }
        }.getOrDefault(emptyList())
    }
}
