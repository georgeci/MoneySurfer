package com.georgeci.moneysurfer.appconfig

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Resolution order is `Debug > Local > RemoteGlobal > Build > key.default`. */
enum class ConfigLayer {
    Debug,
    Local,
    RemoteGlobal,
    Build,
}

/**
 * What one layer holds for one key. Absent and undecodable are deliberately distinct: both stop
 * the layer from winning, but only the debug panel needs to tell them apart.
 */
sealed interface LayerValue<out T : Any> {

    /** The layer does not hold this key. Never means `false`. */
    data object Absent : LayerValue<Nothing>

    /** Stored, but the codec rejected it. Logged, non-fatal, treated as absent for resolution. */
    data class Undecodable(val raw: String) : LayerValue<Nothing>

    data class Present<out T : Any>(val value: T) : LayerValue<T>
}

/**
 * One layer of the chain.
 *
 * [peek] is synchronous, so every source keeps an in-memory mirror of its backing store. The
 * mirror is warmed by [hydrate] and kept current by [changes] — collecting that flow refreshes
 * the mirror before it emits, which is what makes `Config.resolve` correct while the debug panel
 * is on screen. Sources that own their writes also refresh on write.
 */
interface ConfigSource {

    val layer: ConfigLayer

    /** Reads the warmed mirror. Cold (un-hydrated, unobserved) sources report [LayerValue.Absent]. */
    fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T>

    /**
     * Emits once with the current state, then again after every change this source serves.
     *
     * Must not fail. A source whose backing store cannot be read reports [isDegraded] and emits as
     * if it were empty: this flow is collected on the startup path, before a start route exists, so
     * a throw here takes the whole app down rather than costing one layer.
     */
    val changes: Flow<Unit>

    /** Warms the in-memory mirror. Idempotent. Must not fail — see [isDegraded]. */
    suspend fun hydrate()

    /**
     * `true` while this source's backing store cannot be read. Its keys then resolve as absent, so
     * values fall through to the layers below and finally to `key.default` — correct, but not
     * necessarily current, which is why the debug panel surfaces it.
     *
     * Live rather than latched: a store that becomes readable again clears the flag on its next
     * successful read.
     */
    val isDegraded: Boolean get() = false
}

/**
 * Host-declared, in-memory, read-only. The only layer that holds typed values directly — there is
 * no store to encode for.
 */
interface BuildConfigSource : ConfigSource {
    override val layer: ConfigLayer get() = ConfigLayer.Build
}

/**
 * Declares the Build layer for one host, replacing the per-host flag data classes.
 *
 * ```kotlin
 * BuildConfigSource {
 *     put(HostConfigKeys.isOffline, false)
 *     put(HostConfigKeys.transferEnabled, true)
 * }
 * ```
 *
 * [BuildConfigSourceBuilder.put] is a function, not an infix `to`: a type mismatch in `key to
 * value` resolves silently to `kotlin.to` and produces a `Pair` the builder never sees.
 */
fun BuildConfigSource(declare: BuildConfigSourceBuilder.() -> Unit): BuildConfigSource =
    MapBuildConfigSource(BuildConfigSourceBuilder().apply(declare).values.toMap())

class BuildConfigSourceBuilder internal constructor() {
    internal val values: MutableMap<String, Any> = mutableMapOf()

    fun <T : Any> put(key: ConfigKey<T>, value: T) {
        values[key.name] = value
    }
}

private class MapBuildConfigSource(private val values: Map<String, Any>) : BuildConfigSource {
    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> {
        @Suppress("UNCHECKED_CAST") // BuildConfigSourceBuilder.put is the only writer and it is typed.
        val value = values[key.name] as T? ?: return LayerValue.Absent
        return LayerValue.Present(value)
    }

    override val changes: Flow<Unit> = flowOf(Unit)

    override suspend fun hydrate() = Unit
}

/**
 * Server-owned flags. Serves only `remoteOverridable` keys — the engine enforces that centrally,
 * so a source that ignored the rule still cannot override a host fact or a user setting.
 *
 * `Empty` lives here rather than in a `no-op` module so the offline host can bind it without
 * depending on the Firestore-bound implementation.
 */
interface RemoteGlobalConfigSource : ConfigSource {
    override val layer: ConfigLayer get() = ConfigLayer.RemoteGlobal

    companion object {
        val Empty: RemoteGlobalConfigSource = object : RemoteGlobalConfigSource {
            override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = LayerValue.Absent
            override val changes: Flow<Unit> = flowOf(Unit)
            override suspend fun hydrate() = Unit
        }
    }
}

/** Device-local user settings. The only writable layer on the production path. */
interface LocalConfigSource : ConfigSource {
    override val layer: ConfigLayer get() = ConfigLayer.Local

    suspend fun <T : Any> write(key: SettingKey<T>, value: T)
}

/**
 * QA overrides, backed by their own store so resetting them never touches user settings.
 *
 * Release builds bind [Empty]: the layer stays in the chain and always resolves absent.
 * [isActive] is the one signal the rest of the app uses to decide whether the debug surface
 * exists at all.
 */
interface DebugConfigSource : ConfigSource {
    override val layer: ConfigLayer get() = ConfigLayer.Debug

    /** `false` for [Empty] — no store, writes are rejected. */
    val isActive: Boolean

    /** Stores [raw] verbatim; the caller validates it through the key's codec first. */
    suspend fun override(key: ConfigKey<*>, raw: String)

    suspend fun clear(key: ConfigKey<*>)

    suspend fun clearAll()

    companion object {
        val Empty: DebugConfigSource = object : DebugConfigSource {
            override val isActive: Boolean = false
            override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = LayerValue.Absent
            override val changes: Flow<Unit> = flowOf(Unit)
            override suspend fun hydrate() = Unit
            override suspend fun override(key: ConfigKey<*>, raw: String) = Unit
            override suspend fun clear(key: ConfigKey<*>) = Unit
            override suspend fun clearAll() = Unit
        }
    }
}
