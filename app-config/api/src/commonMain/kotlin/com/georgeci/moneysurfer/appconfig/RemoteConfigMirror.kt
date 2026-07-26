package com.georgeci.moneysurfer.appconfig

import kotlinx.coroutines.flow.Flow

/**
 * Persistent copy of the last accepted `appConfig/flags` payload, so the RemoteGlobal layer resolves
 * before — and without — a network round trip.
 *
 * Two properties depend on it. A cold start reads the mirror during `hydrate()`, long before any
 * fetch could return, so `Config.snapshot` is correct on the first frame rather than a few hundred
 * milliseconds later. And a device that never reaches Firestore again keeps resolving the last
 * values the server did send, instead of silently falling through to Build.
 *
 * Declared here rather than in `app-config/remote` because it is storage, not Firestore: the
 * implementation lives in `data-local` next to the other DataStore-backed layers, and `api` must
 * stay SDK-free so both sides can see the contract.
 *
 * Values are raw encoded strings, exactly as `ConfigCodec` expects them — the mirror never decodes,
 * so a value this build cannot read stays visible to the debug panel instead of being dropped by the
 * writer.
 */
interface RemoteConfigMirror {

    /** Reads the warmed in-memory copy. `null` means the mirror does not hold this name. */
    fun raw(name: String): String?

    /** Emits once with the current state, then again after every [replaceAll]. Must not fail. */
    val changes: Flow<Unit>

    /** Warms the in-memory copy from storage. Idempotent. Must not fail — see [isDegraded]. */
    suspend fun hydrate()

    /** `true` while the backing store cannot be read; cleared by the next successful read. */
    val isDegraded: Boolean get() = false

    /**
     * Replaces the whole mirror with [values] — deliberately not a merge.
     *
     * Retraction has to propagate: a key the owner deletes from `appConfig/flags` must stop being
     * served, and a merge would pin the last value it ever had on every device forever. The same
     * reasoning makes an absent document clear the mirror rather than preserve it.
     */
    suspend fun replaceAll(values: Map<String, String>)
}
