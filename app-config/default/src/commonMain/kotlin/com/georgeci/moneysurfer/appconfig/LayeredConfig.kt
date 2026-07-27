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
 * @param overlay the layer a write has to retire, because it outranks the one being written to.
 *   Defaults to [SessionConfigOverlay.Inactive] so an engine assembled without it behaves exactly
 *   as it did before the overlay existed.
 */
class LayeredConfig(
    private val layers: List<ConfigSource>,
    private val local: LocalConfigSource,
    private val failFastOnEarlySnapshot: Boolean,
    private val overlay: SessionConfigOverlay = SessionConfigOverlay.Inactive,
) : Config {

    private val log = Logger.withTag(TAG)
    private val hydration = Mutex()

    @Volatile
    private var hydrated: Boolean = false

    /**
     * Layers whose `hydrate()` threw. Latched, because a source that throws rather than reporting
     * [ConfigSource.isDegraded] gives us no way to observe its recovery — the DataStore-backed
     * layers do report it, and those clear on their own.
     */
    @Volatile
    private var hydrationFailures: Set<ConfigLayer> = emptySet()

    override val degradedLayers: Set<ConfigLayer>
        get() = hydrationFailures + layers.filter { it.isDegraded }.map { it.layer }

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
     * flow as soon as the store is readable again, and [ConfigSource.isDegraded] stops reporting it.
     *
     * A source is expected to swallow its own read failures and report [ConfigSource.isDegraded];
     * the catch here is the backstop for one that throws anyway.
     */
    override suspend fun hydrate() {
        if (hydrated) return
        hydration.withLock {
            if (hydrated) return
            hydrationFailures = layers.mapNotNullTo(mutableSetOf()) { source -> source.hydrateOrNull() }
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

        /**
         * Retiring the overlay entry is what keeps the write visible: the overlay outranks Local,
         * so a value picked while it still holds this key would be stored and then not shown. It is
         * a no-op in a normal session, where the overlay holds nothing at all.
         *
         * **After** the write, not before. Releasing first would expose the key's default for the
         * whole duration of `local.write` — a Room write plus an outbox enqueue in a second
         * database — and, if that write threw, would leave the value gone from both the overlay and
         * the store, snapping the running UI to a default the user never chose. Held-then-released
         * only ever shows the old value; released-then-failed shows neither.
         */
        override suspend fun set(value: T) {
            local.write(key, value)
            overlay.release(key.name)
        }
    }

    override fun <T : Any> resolve(key: ConfigKey<T>): ConfigResolution<T> {
        val perLayer = mutableMapOf<ConfigLayer, LayerValue<T>>()
        var winner: ConfigLayer? = null
        var winningValue: T? = null
        layers.forEach { source ->
            // What the layer holds goes into `perLayer` verbatim; what it is *allowed* to serve is a
            // separate question. Reporting a refused value as absent would leave the debug panel
            // unable to tell "the server sent nothing" from "the server sent something we ignored".
            val held = source.held(key)
            perLayer[source.layer] = held
            val served = if (source.mayServe(key)) held else LayerValue.Absent
            if (winner == null && served is LayerValue.Present) {
                winner = source.layer
                winningValue = served.value
            }
        }
        return ConfigResolution(
            value = winningValue ?: key.default,
            winner = winner,
            perLayer = perLayer,
        )
    }

    /**
     * The remote opt-in, applied centrally: a `RemoteGlobalConfigSource` that ignored
     * [ConfigKey.remoteOverridable] still cannot override a host fact or seed a user setting.
     */
    private fun ConfigSource.mayServe(key: ConfigKey<*>): Boolean =
        layer != ConfigLayer.RemoteGlobal || key.remoteOverridable

    /**
     * What the layer holds, whatever the engine will do with it. Also where an undecodable stored
     * value — and a remote value the opt-in refuses — is logged: once, at the resolution boundary,
     * instead of in every source.
     */
    private fun <T : Any> ConfigSource.held(key: ConfigKey<T>): LayerValue<T> {
        val value = peek(key)
        when {
            value is LayerValue.Undecodable ->
                log.w { "[$layer] ${key.name} holds an undecodable value — treating the layer as absent" }
            value is LayerValue.Present && !mayServe(key) ->
                log.w { "[$layer] ${key.name} is not remoteOverridable — value refused, resolving without it" }
        }
        return value
    }

    private companion object {
        const val TAG = "Config"
    }
}
