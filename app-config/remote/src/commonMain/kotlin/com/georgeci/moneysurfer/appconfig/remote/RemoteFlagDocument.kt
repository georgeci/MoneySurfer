package com.georgeci.moneysurfer.appconfig.remote

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.koin.core.annotation.Single

/** Shared by the two halves of this layer — they are one subsystem in the log. */
internal const val REMOTE_CONFIG_LOG_TAG = "RemoteConfig"

/**
 * One read of the server-owned flag document, reduced to the shape the layer needs.
 *
 * A seam rather than a direct Firestore call inside the source: `FirebaseFirestore` is a final
 * gitlive class with no test double, so without this the filtering and mirroring rules — which are
 * the part with actual decisions in them — could not be tested at all.
 */
fun interface RemoteFlagDocument {
    suspend fun fetch(): RemoteFlagFetch
}

/**
 * Why the two cases are distinct: a document that was read and holds nothing must **clear** the
 * mirror, while a document that could not be read must **leave it alone**. Collapsing them into an
 * empty map would drop every flag the moment a device went offline; collapsing them the other way
 * would make a retracted flag stick forever on a device that missed the retraction.
 */
sealed interface RemoteFlagFetch {

    /** The server answered. [values] is empty when the document does not exist. */
    data class Read(val values: Map<String, String>) : RemoteFlagFetch

    /** Unreachable or unreadable. Already logged; the mirror keeps what it has. */
    data object Unavailable : RemoteFlagFetch
}

/**
 * Reads `appConfig/flags` as a free-form map.
 *
 * Free-form in its *names*, not its value types: everything is decoded to `String`, which is the
 * form every `ConfigCodec` already expects. gitlive's decoder stringifies whatever the field holds,
 * so a boolean typed into the Firebase Console arrives as `"true"` and `BooleanConfigCodec` accepts
 * it — the owner does not have to remember to quote flag values. A value no codec can read is still
 * mirrored and surfaces as `LayerValue.Undecodable`, so the debug panel shows what the server
 * actually sent rather than a fallback dressed up as the current value.
 *
 * This is the second reader of the `appConfig` collection and deliberately not the first: the
 * app-version gate keeps its own typed `appConfig/mobile` document and its awaitable, fail-open
 * `refresh()`. See ADR-004 → "Version gate stays as it is".
 */
@Single(binds = [RemoteFlagDocument::class])
class FirestoreRemoteFlagDocument(private val firestore: FirebaseFirestore) : RemoteFlagDocument {

    private val log = Logger.withTag(REMOTE_CONFIG_LOG_TAG)

    @Suppress("TooGenericExceptionCaught") // Any fetch failure must degrade to the mirror, not propagate.
    override suspend fun fetch(): RemoteFlagFetch = try {
        firestore.collection(COLLECTION).document(DOCUMENT).get().toFetch()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // Warning, not error: being offline is the expected case here, and the mirror is exactly
        // what makes it harmless. A permanent failure shows up as a stale debug panel, not a crash.
        log.w(failure) { "$COLLECTION/$DOCUMENT unreadable — resolving from the mirrored values" }
        RemoteFlagFetch.Unavailable
    }

    /**
     * A **missing** document is only an answer when the server said so.
     *
     * `get()` falls back to Firestore's local cache when the network is unreachable, and a cache
     * miss produces a perfectly ordinary snapshot with `exists == false` rather than an exception.
     * Reading that as "the server has no flags" would clear the mirror on a device that is merely
     * offline — and for a kill switch, clearing means reverting to `default = true`, i.e. switching
     * the killed feature back on. `isFromCache` is what separates the two, so an uncached miss is
     * reported as [RemoteFlagFetch.Unavailable] and a genuine server-side deletion still propagates.
     *
     * A document that *exists* is taken from either source: a cached hit is the last thing the
     * server sent, which is exactly what the mirror already holds.
     */
    private fun DocumentSnapshot.toFetch(): RemoteFlagFetch = when {
        exists -> RemoteFlagFetch.Read(data(FLAG_MAP))
        metadata.isFromCache -> {
            log.w { "$COLLECTION/$DOCUMENT missing from cache while offline — keeping the mirrored values" }
            RemoteFlagFetch.Unavailable
        }
        else -> RemoteFlagFetch.Read(emptyMap())
    }

    private companion object {
        const val COLLECTION = "appConfig"
        const val DOCUMENT = "flags"
        val FLAG_MAP = MapSerializer(String.serializer(), String.serializer())
    }
}
