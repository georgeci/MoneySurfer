package com.georgeci.moneysurfer.appconfig

import com.georgeci.moneysurfer.domain.config.ConfigDebugLayerCell
import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Renders the whole registry for the QA panel and takes string-keyed writes back.
 *
 * Nothing typed crosses into `feature/settings`: rows are pre-rendered strings, and an override
 * arrives as `(name, raw)` and is parsed through the codec the registry resolves for that name.
 */
@Single(binds = [DebugConfigInspector::class])
class DebugConfigInspectorImpl(
    private val config: Config,
    private val registry: ConfigRegistry,
    private val debugSource: DebugConfigSource,
) : DebugConfigInspector {

    override val isAvailable: Boolean get() = debugSource.isActive

    override val rows: Flow<List<ConfigDebugRow>> = config.changes
        .map { registry.keys.map { key -> key.toRow() } }
        .distinctUntilChanged()

    override suspend fun override(name: String, raw: String): Result<Unit> {
        val key = registry.find(name)
            ?: return Result.failure(IllegalArgumentException("Unknown configuration key '$name'"))
        if (key.codec.decode(raw) == null) {
            return Result.failure(IllegalArgumentException("'$raw' is not a valid value for '$name'"))
        }
        debugSource.override(key, raw)
        return Result.success(Unit)
    }

    override suspend fun clearOverride(name: String) {
        registry.find(name)?.let { debugSource.clear(it) }
    }

    override suspend fun resetAll() = debugSource.clearAll()

    private fun <T : Any> ConfigKey<T>.toRow(): ConfigDebugRow {
        val resolution = config.resolve(this)
        return ConfigDebugRow(
            name = name,
            effectiveValue = codec.encode(resolution.value),
            winner = resolution.winner?.name ?: DEFAULT_WINNER,
            kind = codec.valueKind.toRowKind(),
            overridden = resolution.winner == ConfigLayer.Debug,
            layers = ConfigLayer.entries.map { layer ->
                resolution.perLayer[layer].toCell(layer, this)
            },
        )
    }

    private fun <T : Any> LayerValue<T>?.toCell(layer: ConfigLayer, key: ConfigKey<T>): ConfigDebugLayerCell =
        when (this) {
            null, LayerValue.Absent -> ConfigDebugLayerCell(layer.name, value = null, undecodable = false)
            is LayerValue.Undecodable -> ConfigDebugLayerCell(layer.name, value = raw, undecodable = true)
            is LayerValue.Present -> ConfigDebugLayerCell(
                layer = layer.name,
                value = key.codec.encode(value),
                undecodable = false,
            )
        }

    private fun ConfigValueKind.toRowKind(): ConfigDebugRowKind = when (this) {
        ConfigValueKind.Bool -> ConfigDebugRowKind.Bool
        is ConfigValueKind.Choice -> ConfigDebugRowKind.Choice(choices)
        ConfigValueKind.FreeText -> ConfigDebugRowKind.FreeText
    }

    private companion object {
        const val DEFAULT_WINNER = "default"
    }
}
