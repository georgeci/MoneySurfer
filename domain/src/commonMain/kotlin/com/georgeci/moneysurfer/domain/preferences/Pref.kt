package com.georgeci.moneysurfer.domain.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single typed preference: a value that has a current snapshot, observable changes, and a
 * setter. "Cleared" / "absent" is represented in the value type itself (e.g. `Pref<UserId?>`
 * with `null` for "no current user", `Pref<Boolean>` with `false` for unset flags) so the
 * interface stays minimal — no `clear()` ceremony at the call sites.
 */
interface Pref<T> {
    val flow: Flow<T>
    suspend fun set(value: T)

    companion object {
        /** Volatile in-memory implementation backed by a [MutableStateFlow]. */
        fun <T> inMemory(initial: T): Pref<T> = InMemoryPref(initial)
    }
}

private class InMemoryPref<T>(initial: T) : Pref<T> {
    private val state = MutableStateFlow(initial)
    override val flow: Flow<T> = state
    override suspend fun set(value: T) {
        state.value = value
    }
}
