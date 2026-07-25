package com.georgeci.moneysurfer.domain.config

import kotlinx.coroutines.flow.Flow

/**
 * Read/write view of every configuration key, for the QA panel in debug builds.
 *
 * Everything is pre-rendered as strings and the write path is keyed by name, so no `ConfigKey<*>`
 * — and no `app-config` dependency — reaches `feature/settings`.
 */
interface DebugConfigInspector {

    /**
     * `false` in release builds, where the Debug layer is empty. The panel and its entry point are
     * hidden rather than shown broken.
     */
    val isAvailable: Boolean

    val rows: Flow<List<ConfigDebugRow>>

    /** Fails when [raw] does not decode through the key's codec, or when the key is unknown. */
    suspend fun override(name: String, raw: String): Result<Unit>

    suspend fun clearOverride(name: String)

    suspend fun resetAll()
}

/**
 * One key as the panel sees it.
 *
 * @param effectiveValue the resolved value, encoded.
 * @param winner name of the layer that won, or `"default"` when no layer holds the key.
 * @param kind which control to render — a text field for `PaletteSource`'s `PRESET:<seed>` format
 *   is a routine way to fail.
 * @param overridden `true` when the Debug layer is what won, i.e. this row can be cleared.
 */
data class ConfigDebugRow(
    val name: String,
    val effectiveValue: String,
    val winner: String,
    val kind: ConfigDebugRowKind,
    val overridden: Boolean,
    val layers: List<ConfigDebugLayerCell>,
)

sealed interface ConfigDebugRowKind {
    data object Bool : ConfigDebugRowKind
    data class Choice(val choices: List<String>) : ConfigDebugRowKind
    data object FreeText : ConfigDebugRowKind
}

/**
 * One layer's contribution. [value] is `null` when the layer does not hold the key; [undecodable]
 * distinguishes "stored but rejected by the codec" from that, which is the whole reason the panel
 * shows per-layer cells at all.
 */
data class ConfigDebugLayerCell(
    val layer: String,
    val value: String?,
    val undecodable: Boolean,
)
