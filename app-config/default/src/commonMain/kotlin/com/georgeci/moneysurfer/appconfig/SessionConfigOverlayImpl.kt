package com.georgeci.moneysurfer.appconfig

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

/**
 * [SessionConfigOverlay] over a `StateFlow` map — the whole layer, since there is no store to read.
 *
 * `hydrate()` is a no-op and `isDegraded` is never true: an in-memory map cannot fail to be read,
 * which is also why this is the one layer that needs no mirror plumbing.
 *
 * The state flow doubles as [changes], so holding or releasing values re-resolves every observed
 * key — that is what actually keeps the theme on screen through a logout.
 *
 * Every mutation goes through [update] rather than assigning `state.value`. The three callers run
 * on different coroutines — [release] from a settings write, [hold] from the account wipe, [clear]
 * from session start — and a read-modify-write between two of them loses one: a `release` racing a
 * `hold` would reinstate the entry the write was meant to retire, leaving the new value invisible
 * underneath it for the rest of the session.
 */
@Single(binds = [SessionConfigOverlay::class])
class SessionConfigOverlayImpl : SessionConfigOverlay {

    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = key.layerValueOf(state.value[key.name])

    override val changes: Flow<Unit> = state.map { }

    override suspend fun hydrate() = Unit

    override fun hold(values: Map<String, String>) {
        state.update { it + values }
    }

    override fun release(name: String) {
        state.update { it - name }
    }

    override fun clear() {
        state.value = emptyMap()
    }
}
