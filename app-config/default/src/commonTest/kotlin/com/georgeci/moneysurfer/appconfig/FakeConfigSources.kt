package com.georgeci.moneysurfer.appconfig

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for a store-backed layer. Holds raw strings and decodes through the key's
 * codec, exactly like the real DataStore-backed sources — so an undecodable value behaves here the
 * way it does in production.
 *
 * [hydrated] models the warm/cold split: a cold source reports everything absent, which is what
 * makes a pre-hydration `snapshot()` resolve Build and defaults only.
 */
internal class FakeStoreConfigSource(
    override val layer: ConfigLayer,
    initial: Map<String, String> = emptyMap(),
    private val warmOnlyAfterHydrate: Boolean = false,
) : ConfigSource {

    private val state = MutableStateFlow(initial)
    private var hydrated = !warmOnlyAfterHydrate

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> {
        if (!hydrated) return LayerValue.Absent
        val raw = state.value[key.name] ?: return LayerValue.Absent
        return key.codec.decode(raw)?.let { LayerValue.Present(it) } ?: LayerValue.Undecodable(raw)
    }

    override val changes: Flow<Unit> = state.map { }

    override suspend fun hydrate() {
        hydrated = true
    }

    fun put(name: String, raw: String) {
        state.value = state.value + (name to raw)
    }

    fun remove(name: String) {
        state.value = state.value - name
    }
}

internal class FakeLocalConfigSource(
    initial: Map<String, String> = emptyMap(),
) : LocalConfigSource {

    private val delegate = FakeStoreConfigSource(ConfigLayer.Local, initial)

    /** Every write this source has seen, so a test can assert a read path performed none. */
    val writes: MutableList<String> = mutableListOf()

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = delegate.peek(key)
    override val changes: Flow<Unit> = delegate.changes
    override suspend fun hydrate() = delegate.hydrate()

    override suspend fun <T : Any> write(key: SettingKey<T>, value: T) {
        writes += key.name
        delegate.put(key.name, key.codec.encode(value))
    }
}

internal class FakeDebugConfigSource(
    override val isActive: Boolean = true,
    initial: Map<String, String> = emptyMap(),
) : DebugConfigSource {

    private val delegate = FakeStoreConfigSource(ConfigLayer.Debug, initial)

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = delegate.peek(key)
    override val changes: Flow<Unit> = delegate.changes
    override suspend fun hydrate() = delegate.hydrate()

    override suspend fun override(key: ConfigKey<*>, raw: String) = delegate.put(key.name, raw)
    override suspend fun clear(key: ConfigKey<*>) = delegate.remove(key.name)
    override suspend fun clearAll() = Unit
}

internal class FakeRemoteGlobalConfigSource(
    initial: Map<String, String> = emptyMap(),
) : RemoteGlobalConfigSource {

    private val delegate = FakeStoreConfigSource(ConfigLayer.RemoteGlobal, initial)

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = delegate.peek(key)
    override val changes: Flow<Unit> = delegate.changes
    override suspend fun hydrate() = delegate.hydrate()
}
