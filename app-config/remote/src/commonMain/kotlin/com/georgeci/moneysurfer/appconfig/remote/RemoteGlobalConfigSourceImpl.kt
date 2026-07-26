package com.georgeci.moneysurfer.appconfig.remote

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigRegistry
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.appconfig.RemoteGlobalConfigSource
import com.georgeci.moneysurfer.appconfig.layerValueOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * The RemoteGlobal layer, backed by `appConfig/flags`.
 *
 * Reads never touch the network: every value is served from [RemoteConfigMirror], which
 * [RemoteFlagDocument] refills on launch and on foreground return. That is what makes a cold start
 * resolve on the first frame and an offline launch resolve the last values the server sent.
 *
 * Only the online host binds this. The offline build keeps `RemoteGlobalConfigSource.Empty` from
 * `app-config/api` and never has `app-config/remote` on its classpath at all.
 */
@Single(binds = [RemoteGlobalConfigSource::class])
class RemoteGlobalConfigSourceImpl(
    private val document: RemoteFlagDocument,
    private val registry: ConfigRegistry,
    private val mirror: RemoteConfigMirror,
) : RemoteGlobalConfigSource {

    private val log = Logger.withTag(REMOTE_CONFIG_LOG_TAG)

    /** Serializes overlapping refreshes — a foreground return can land while a launch fetch is in flight. */
    private val fetching = Mutex()

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = key.layerValueOf(mirror.raw(key.name))

    override val changes: Flow<Unit> = mirror.changes

    override suspend fun hydrate() = mirror.hydrate()

    override val isDegraded: Boolean get() = mirror.isDegraded

    override suspend fun refresh() = fetching.withLock {
        when (val fetch = document.fetch()) {
            // Already logged at the fetch site. Keeping the mirror is the point of having one.
            RemoteFlagFetch.Unavailable -> Unit
            is RemoteFlagFetch.Read -> mirrorOrLog(fetch.values.filterKeys(::isServable))
        }
    }

    /**
     * The mirror write is the one step here that can still throw — a full disk, a read-only store —
     * and this runs on a fire-and-forget coroutine with nobody to catch it. Swallowing it costs one
     * stale refresh; propagating it would take the app down for a flag update.
     */
    @Suppress("TooGenericExceptionCaught") // Any store failure must degrade, not propagate.
    private suspend fun mirrorOrLog(values: Map<String, String>) = try {
        mirror.replaceAll(values)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // Error severity: `CrashReportingLogWriter` turns this into a Crashlytics non-fatal, which is
        // the only signal that an install has stopped tracking server-side flags.
        log.e(failure) { "flag mirror unwritable — resolving from the previous values" }
    }

    /**
     * The `remoteOverridable` opt-in, applied at the write side.
     *
     * `LayeredConfig` enforces it centrally too, so this is not what makes the guarantee hold — it is
     * what keeps a refused name out of the mirror entirely. Without it a stray `host.is_offline` in
     * the document would sit in local storage being refused on every single read, and would show up
     * in the debug panel as a value the RemoteGlobal layer holds.
     */
    private fun isServable(name: String): Boolean {
        val key = registry.find(name)
        if (key == null) {
            log.w { "$name is not a known configuration key — ignoring it" }
            return false
        }
        if (!key.remoteOverridable) {
            log.w { "$name is not remoteOverridable — ignoring it" }
            return false
        }
        return true
    }
}
