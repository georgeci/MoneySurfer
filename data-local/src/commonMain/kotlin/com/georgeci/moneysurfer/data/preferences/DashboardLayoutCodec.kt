package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType

/**
 * Flat string encoding of a [DashboardLayoutConfig], one DataStore key instead of a nested
 * document:
 *
 * ```text
 * Balance:1:Hero|Accounts:1:Compact:strip|Goals:0:Hero
 * ```
 *
 * `type:enabled:size[:variant]`, items separated by `|`. Types and sizes are enum names, but
 * `variant` is free-form widget-defined text, so the separators (and the escape character itself)
 * are percent-escaped on the way in and restored on the way out — a variant containing `|` would
 * otherwise re-split into a bogus extra item.
 *
 * Decoding never throws and never fails the read: anything unparseable is skipped and
 * [DashboardLayoutConfig.normalized] fills the gaps from the default layout, so a store written by
 * a newer build (or corrupted on disk) degrades to a usable dashboard rather than an empty one.
 */
internal object DashboardLayoutCodec {

    private const val ITEM_SEPARATOR = '|'
    private const val FIELD_SEPARATOR = ':'
    private const val ENABLED = "1"
    private const val DISABLED = "0"
    private const val MIN_FIELDS = 3

    /** Escape char first on the way in, last on the way out — otherwise escapes eat each other. */
    private val VARIANT_ESCAPES = listOf(
        "%" to "%25",
        ITEM_SEPARATOR.toString() to "%7C",
        FIELD_SEPARATOR.toString() to "%3A",
    )

    fun encode(config: DashboardLayoutConfig): String =
        config.items.joinToString(ITEM_SEPARATOR.toString()) { item ->
            buildString {
                append(item.type.name)
                append(FIELD_SEPARATOR)
                append(if (item.enabled) ENABLED else DISABLED)
                append(FIELD_SEPARATOR)
                append(item.cardStyle.size.name)
                item.cardStyle.variant?.let {
                    append(FIELD_SEPARATOR)
                    append(escapeVariant(it))
                }
            }
        }

    private fun escapeVariant(variant: String): String =
        VARIANT_ESCAPES.fold(variant) { acc, (raw, escaped) -> acc.replace(raw, escaped) }

    private fun unescapeVariant(variant: String): String =
        VARIANT_ESCAPES.reversed().fold(variant) { acc, (raw, escaped) -> acc.replace(escaped, raw) }

    fun decode(stored: String): DashboardLayoutConfig = decodeOrNull(stored) ?: DashboardLayoutConfig.DEFAULT

    /**
     * `null` when nothing in [stored] parsed, so a caller that needs to tell "the user is on the
     * default layout" from "the stored string is unreadable" can. The configuration engine does:
     * reporting garbage as a decoded DEFAULT would make that layer *win*, hiding the corruption
     * from the debug panel and overwriting the string on the next save.
     */
    fun decodeOrNull(stored: String): DashboardLayoutConfig? {
        if (stored.isBlank()) return null
        val items = stored.split(ITEM_SEPARATOR).mapNotNull(::decodeItem)
        return if (items.isEmpty()) null else DashboardLayoutConfig(items).normalized()
    }

    private fun decodeItem(stored: String): DashboardLayoutItem? {
        val fields = stored.split(FIELD_SEPARATOR, limit = MIN_FIELDS + 1)
        if (fields.size < MIN_FIELDS) return null
        val type = DashboardWidgetType.entries.firstOrNull { it.name == fields[0] } ?: return null
        val size = DashboardWidgetSize.entries.firstOrNull { it.name == fields[2] } ?: DashboardWidgetSize.Hero
        return DashboardLayoutItem(
            type = type,
            enabled = fields[1] != DISABLED,
            cardStyle = DashboardCardStyle(
                size = size,
                variant = fields.getOrNull(MIN_FIELDS)?.takeIf(String::isNotBlank)?.let(::unescapeVariant),
            ),
        )
    }
}
