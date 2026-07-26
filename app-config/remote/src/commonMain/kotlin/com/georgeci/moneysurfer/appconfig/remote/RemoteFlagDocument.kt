package com.georgeci.moneysurfer.appconfig.remote

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
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
        // Message, not throwable: `CrashReportingLogWriter` turns any Warn carrying a `Throwable`
        // into a Crashlytics non-fatal, and this branch is the *expected* one — every offline
        // foreground return lands here. Attaching the failure would file one non-fatal per
        // foreground return per offline user and bury real crashes. The breadcrumb is the signal
        // worth keeping; a permanent failure surfaces through `isDegraded` instead.
        log.w { "$COLLECTION/$DOCUMENT unreadable (${failure.message}) — resolving from the mirrored values" }
        RemoteFlagFetch.Unavailable
    }

    /**
     * A **missing** document is only an answer when the server said so — and the SDK guarantees
     * that for us, rather than this code having to test for it.
     *
     * `get()` does fall back to Firestore's local cache when the network is unreachable, but it
     * does not hand back an empty snapshot in that case: `DocumentReference` converts
     * `!exists && metadata.isFromCache` into an `UNAVAILABLE` failure ("Failed to get document
     * because the client is offline") on Android, iOS and the desktop JVM alike. So an offline
     * cache miss arrives here as a throw and is caught above as [RemoteFlagFetch.Unavailable], and
     * a snapshot that does reach this function with `exists == false` really is the server saying
     * the document is gone.
     *
     * That distinction is the one this layer turns on: clearing the mirror on a merely-offline
     * device would revert a kill switch to its `default = true` and switch the killed feature back
     * on. It is delivered by the `try`/`catch` above, not by anything in here.
     */
    private fun DocumentSnapshot.toFetch(): RemoteFlagFetch {
        if (!exists) return RemoteFlagFetch.Read(emptyMap())
        // A field the owner cleared to `null` instead of deleting is absent, not the string "null":
        // gitlive decodes every field through `toString()`, so a nullable value serializer is what
        // keeps `null` from being mirrored — and `StringConfigCodec` would happily serve it.
        return RemoteFlagFetch.Read(data(FLAG_MAP).mapNotNull { (name, value) -> value?.let { name to it } }.toMap())
    }

    private companion object {
        const val COLLECTION = "appConfig"
        const val DOCUMENT = "flags"
        val FLAG_MAP = MapSerializer(String.serializer(), String.serializer().nullable)
    }
}
