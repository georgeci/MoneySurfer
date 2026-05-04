package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.data.datastore.core.PreferenceProperty
import com.georgeci.moneysurfer.domain.preferences.Pref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Wrap a `PreferenceProperty<T>` as a domain `Pref<T>` 1:1 (no value mapping). */
fun <T> PreferenceProperty<T>.asPref(): Pref<T> = object : Pref<T> {
    override val flow: Flow<T> = this@asPref.flow
    override suspend fun set(value: T) = this@asPref.set(value)
}

/**
 * Wrap a `PreferenceProperty<S>` as a domain `Pref<T>` with a value-type bridge. Used to
 * present DataStore's empty-string sentinel as `null` to the domain (e.g. session-pointer
 * ids).
 */
fun <S, T> PreferenceProperty<S>.asPref(
    fromStored: (S) -> T,
    toStored: (T) -> S,
): Pref<T> = object : Pref<T> {
    override val flow: Flow<T> = this@asPref.flow.map(fromStored)
    override suspend fun set(value: T) = this@asPref.set(toStored(value))
}
