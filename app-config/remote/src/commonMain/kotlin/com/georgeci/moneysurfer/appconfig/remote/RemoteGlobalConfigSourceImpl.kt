package com.georgeci.moneysurfer.appconfig.remote

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigRegistry
import com.georgeci.moneysurfer.appconfig.ConfigSource
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.appconfig.RemoteGlobalConfigSource
import com.georgeci.moneysurfer.appconfig.layerValueOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile

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

    /** Held for the duration of one refresh. See [refresh] for why it is *dropped*, not awaited. */
    private val fetching = Mutex()

    /**
     * `true` once a refresh could not reach the server or could not write what it read.
     *
     * Live rather than latched, matching [ConfigSource.isDegraded]: the next clean refresh clears
     * it. Without this term a layer whose network side is permanently broken — revoked read
     * permission, wrong project, a rules regression — reports healthy forever, and the debug panel
     * presents weeks-old mirrored flags as the server's current answer.
     */
    @Volatile
    private var refreshFailed: Boolean = false

    /**
     * Names already reported as unservable, so each is logged once per process rather than once per
     * refresh. Staging a flag for an unreleased version is the normal rollout pattern, and every
     * install on the current version would otherwise churn the bounded Crashlytics breadcrumb ring
     * on every foreground return, evicting genuinely diagnostic lines from real crash reports.
     *
     * Only ever touched from inside [fetching], so a plain set is enough.
     */
    private val reportedRejections = mutableSetOf<String>()

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = key.layerValueOf(mirror.raw(key.name))

    override val changes: Flow<Unit> = mirror.changes

    override suspend fun hydrate() = mirror.hydrate()

    override val isDegraded: Boolean get() = mirror.isDegraded || refreshFailed

    /**
     * Drops an overlapping refresh instead of queueing behind it.
     *
     * `withLock` would line them up, and the fetch happens *inside* the lock — so on a slow network
     * a burst of foreground returns (each Android Activity recreation is one: rotation, dark mode,
     * font size, locale, split-screen) would each wait out the previous timeout and then pay their
     * own billed document read, long after the event that asked for them. An in-flight refresh is
     * already delivering current values, so a second one has nothing to add.
     */
    override suspend fun refresh() {
        if (!fetching.tryLock()) return
        try {
            when (val fetch = document.fetch()) {
                // Already logged at the fetch site. Keeping the mirror is the point of having one.
                RemoteFlagFetch.Unavailable -> refreshFailed = true
                is RemoteFlagFetch.Read -> refreshFailed = !mirrored(fetch.values.filterKeys(::isServable))
            }
        } finally {
            fetching.unlock()
        }
    }

    /**
     * Writes the mirror, reporting success rather than throwing.
     *
     * The write is the one step here that can still fail — a full disk, a read-only store — and
     * this runs on a fire-and-forget coroutine with nobody to catch it. Swallowing costs one stale
     * refresh; propagating would take the app down for a flag update. The `false` return is what
     * keeps that silence honest: it feeds [isDegraded], so the debug panel stops presenting the
     * previous values as current.
     */
    @Suppress("TooGenericExceptionCaught") // Any store failure must degrade, not propagate.
    private suspend fun mirrored(values: Map<String, String>): Boolean = try {
        mirror.replaceAll(values)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // Error severity: `CrashReportingLogWriter` turns this into a Crashlytics non-fatal. Unlike
        // an unreachable server, an unwritable store is never expected, so it is worth a non-fatal.
        log.e(failure) { "flag mirror unwritable — resolving from the previous values" }
        false
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
            reportOnce(name) { "$name is not a known configuration key — ignoring it" }
            return false
        }
        if (!key.remoteOverridable) {
            reportOnce(name) { "$name is not remoteOverridable — ignoring it" }
            return false
        }
        return true
    }

    private fun reportOnce(name: String, message: () -> String) {
        if (reportedRejections.add(name)) log.w { message() }
    }
}
