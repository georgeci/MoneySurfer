package com.georgeci.moneysurfer.domain.config

/**
 * Re-reads the server-owned flag document into the configuration engine's RemoteGlobal layer.
 *
 * Called on launch and on every foreground return, which is the whole propagation model: there is no
 * snapshot listener, so a flag flipped in the console reaches a running app the next time it comes
 * back to the front, and reaches a cold one on its next start.
 *
 * Deliberately separate from [ConfigHydration]. Hydration is local, fast and awaited before the start
 * route is resolved; this one reaches the network and must never be awaited there — its result lands
 * in the mirror and is picked up by `Config.observe`. Failures are swallowed by the layer, so callers
 * have nothing to handle: an unreachable server leaves the last mirrored values in place.
 *
 * A domain facade for the same reason [ConfigHydration] is one — `navigation` must not depend on
 * `app-config`. The offline build binds it over `RemoteGlobalConfigSource.Empty`, where it no-ops.
 */
fun interface RemoteConfigRefresh {
    suspend fun refresh()
}
