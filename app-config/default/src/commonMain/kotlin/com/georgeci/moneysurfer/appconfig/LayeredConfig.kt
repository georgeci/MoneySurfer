package com.georgeci.moneysurfer.appconfig

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.preferences.Pref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * [Config] over an explicitly ordered layer list.
 *
 * Order is passed in, never collected via `getAll()`: precedence is a correctness property and
 * must not depend on Koin module load order.
 *
 * @param failFastOnEarlySnapshot debug builds throw when [snapshot] runs before [hydrate], so an
 *   ordering mistake surfaces in development instead of shipping as a silently Build-only read.
 */
class LayeredConfig(
    private val layers: List<ConfigSource>,
    private val local: LocalConfigSource,
    private val failFastOnEarlySnapshot: Boolean,
) : Config {

    private val log = Logger.withTag(TAG)
    private val hydration = Mutex()

    @Volatile
    private var hydrated: Boolean = false

    @Volatile
    private var degraded: Set<ConfigLayer> = emptySet()

    override val degradedLayers: Set<ConfigLayer> get() = degraded

    /**
     * Warms every layer it can and reports the ones it could not.
     *
     * A failure here must not stop startup. This is the first suspend call `AppLaunchViewModel`
     * awaits, so a throw would kill the coroutine before a start route is ever resolved — the app
     * would crash-loop on a truncated preferences file until its data is cleared. Falling through to
     * Build values and key defaults leaves a usable app instead, which is the same stance the
     * surrounding startup code already takes for the first-run seeder.
     *
     * The cost is silence, so it is paid for twice: the failure is logged at Error severity (which
     * `CrashReportingLogWriter` turns into a Crashlytics non-fatal) and the layer stays in
     * [degradedLayers] so the debug panel can say "unavailable" rather than implying the layer is
     * simply empty. Recovery needs no retry logic — the mirror refills from the source's `changes`
     * flow as soon as the store is readable again.
     */
    override suspend fun hydrate() {
        if (hydrated) return
        hydration.withLock {
            if (hydrated) return
            degraded = layers.mapNotNullTo(mutableSetOf()) { source -> source.hydrateOrNull() }
            hydrated = true
        }
    }

    /** Returns the layer when hydration failed, `null` when it succeeded. */
    @Suppress("TooGenericExceptionCaught") // Any store failure must degrade, not propagate.
    private suspend fun ConfigSource.hydrateOrNull(): ConfigLayer? = try {
        hydrate()
        null
    } catch (e: Throwable) {
        log.e(e) { "[$layer] hydration failed — resolving without this layer until it recovers" }
        layer
    }

    override val changes: Flow<Unit> = combine(layers.map { it.changes }) { }

    override fun <T : Any> observe(key: ConfigKey<T>): Flow<T> =
        changes.map { resolve(key).value }.distinctUntilChanged()

    override fun <T : Any> snapshot(key: ConfigKey<T>): T {
        if (!hydrated) {
            val message = "Config.snapshot(${key.name}) before hydrate() — only Build and defaults resolve"
            if (failFastOnEarlySnapshot) error(message)
            log.w { message }
        }
        return resolve(key).value
    }

    override fun <T : Any> handle(key: SettingKey<T>): Pref<T> = object : Pref<T> {
        override val flow: Flow<T> = observe(key)
        override suspend fun set(value: T) = local.write(key, value)
    }

    override fun <T : Any> resolve(key: ConfigKey<T>): ConfigResolution<T> {
        val perLayer = mutableMapOf<ConfigLayer, LayerValue<T>>()
        var winner: ConfigLayer? = null
        var winningValue: T? = null
        layers.forEach { source ->
            val layerValue = source.served(key)
            perLayer[source.layer] = layerValue
            if (winner == null && layerValue is LayerValue.Present) {
                winner = source.layer
                winningValue = layerValue.value
            }
        }
        return ConfigResolution(
            value = winningValue ?: key.default,
            winner = winner,
            perLayer = perLayer,
        )
    }

    /**
     * Applies the remote opt-in centrally: a `RemoteGlobalConfigSource` that ignored
     * [ConfigKey.remoteOverridable] still cannot override a host fact or seed a user setting.
     * Also where an undecodable stored value is logged — once, at the resolution boundary, instead
     * of in every source.
     */
    private fun <T : Any> ConfigSource.served(key: ConfigKey<T>): LayerValue<T> {
        if (layer == ConfigLayer.RemoteGlobal && !key.remoteOverridable) return LayerValue.Absent
        val value = peek(key)
        if (value is LayerValue.Undecodable) {
            log.w { "[$layer] ${key.name} holds an undecodable value — treating the layer as absent" }
        }
        return value
    }

    private companion object {
        const val TAG = "Config"
    }
}
