package com.georgeci.moneysurfer.appconfig

import com.georgeci.moneysurfer.domain.preferences.Pref
import kotlinx.coroutines.flow.Flow

/**
 * The configuration engine: resolves every value from ordered layers.
 *
 * Infrastructure, not API. Features never see it — they inject typed domain facades
 * (`UiPreferences`, `SyncSettings`, `HostCapabilities`, `DebugConfigInspector`), and `Config` is
 * injected only into those facade implementations, never into a ViewModel. The fence is the
 * dependency graph: feature modules have no `app-config` on its classpath.
 */
interface Config {

    /** First non-absent layer, re-resolved whenever any layer changes. Falls back to `key.default`. */
    fun <T : Any> observe(key: ConfigKey<T>): Flow<T>

    /**
     * Synchronous resolution. Requires [hydrate] to have completed: called earlier it resolves the
     * Build layer and defaults only, and throws in debug builds so the ordering mistake is not
     * silently shipped.
     */
    fun <T : Any> snapshot(key: ConfigKey<T>): T

    /**
     * Read/write handle for a user setting. Takes [SettingKey] only, so writing to a host- or
     * server-owned flag fails to compile.
     */
    fun <T : Any> handle(key: SettingKey<T>): Pref<T>

    /** Per-layer detail for the debug panel. Layers stay honest here — nothing is clamped. */
    fun <T : Any> resolve(key: ConfigKey<T>): ConfigResolution<T>

    /**
     * Emits once with the current state, then again whenever any layer changes.
     *
     * For consumers that re-resolve *many* keys at once — the debug panel — where per-key
     * [observe] would need one subscription each and would dedupe away the ticks it needs.
     */
    val changes: Flow<Unit>

    /**
     * Warms every layer's in-memory mirror. Awaited once at startup, before the first
     * [snapshot]. Idempotent.
     *
     * Never throws: a layer whose store cannot be read is skipped and named in [degradedLayers],
     * because this runs before a start route exists and a failure here would otherwise take the
     * whole app down with it.
     */
    suspend fun hydrate()

    /**
     * Layers [hydrate] could not warm. Empty on the happy path.
     *
     * A degraded layer resolves as absent, so values fall through to the layers below and finally to
     * `key.default` — correct, but not necessarily current. Kept observable so the debug panel can
     * distinguish "this layer holds nothing" from "this layer could not be read"; the mirror refills
     * on its own once the store is readable again.
     */
    val degradedLayers: Set<ConfigLayer>
}

data class ConfigResolution<T : Any>(
    val value: T,
    /** `null` when no layer holds the key and `key.default` won. */
    val winner: ConfigLayer?,
    /** Absent vs undecodable are distinct — the panel must show which. */
    val perLayer: Map<ConfigLayer, LayerValue<T>>,
)
