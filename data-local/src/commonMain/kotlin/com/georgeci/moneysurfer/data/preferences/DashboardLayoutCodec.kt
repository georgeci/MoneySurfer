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
 * `type:enabled:size[:variant]`, items separated by `|`. Decoding never throws and never fails
 * the read: anything unparseable is skipped and [DashboardLayoutConfig.normalized] fills the gaps
 * from the default layout, so a store written by a newer build (or corrupted on disk) degrades to
 * a usable dashboard rather than an empty one.
 */
internal object DashboardLayoutCodec {

    private const val ITEM_SEPARATOR = '|'
    private const val FIELD_SEPARATOR = ':'
    private const val ENABLED = "1"
    private const val DISABLED = "0"
    private const val MIN_FIELDS = 3

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
                    append(it)
                }
            }
        }

    fun decode(stored: String): DashboardLayoutConfig {
        if (stored.isBlank()) return DashboardLayoutConfig.DEFAULT
        val items = stored.split(ITEM_SEPARATOR).mapNotNull(::decodeItem)
        return if (items.isEmpty()) DashboardLayoutConfig.DEFAULT else DashboardLayoutConfig(items).normalized()
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
                variant = fields.getOrNull(MIN_FIELDS)?.takeIf(String::isNotBlank),
            ),
        )
    }
}
