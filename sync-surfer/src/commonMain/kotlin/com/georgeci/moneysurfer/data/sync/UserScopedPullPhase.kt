package com.georgeci.moneysurfer.data.sync

import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.plugin.PullScope
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.coroutines.cancellation.CancellationException

/**
 * Phase 3 of the pull: collections that hang off `users/{uid}` rather than off a workspace — today
 * only `config`, the per-user settings.
 *
 * Its own class rather than two more methods on [PullRemoteChangesUseCaseImpl], which is at the
 * size where every further branch makes the workspace loop harder to read. The split is also
 * honest: nothing here shares state with the workspace phases — no cursor, no `SyncMetaRepository`,
 * no per-workspace bookkeeping.
 *
 * It runs for every scope that pulls at all, because this is the **only** channel by which a
 * setting changed on another device reaches this one: there is no listener, and one query returning
 * about ten tiny documents is not worth scoping down further.
 */
@Single
class UserScopedPullPhase(
    private val reader: UserCollectionReader,
    private val plugins: List<SyncEntityPlugin>,
    private val session: SessionPointers,
) {

    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<PullSummary> = either {
        if (scope == SyncScope.UploadOnly) return@either EMPTY_SUMMARY
        val uid = session.currentFirebaseUid.first() ?: return@either EMPTY_SUMMARY

        val appliedSummaries = mutableListOf<PullSummary>()

        for ((plugin, collectionName) in pluginsWithCollections()) {
            cancelToken.throwIfCancelled()
            val applied = try {
                fetch(uid, collectionName)?.let { docs ->
                    applyAll(
                        plugin = plugin,
                        collectionName = collectionName,
                        docs = docs,
                        scopeKey = uid,
                        onProgress = onProgress,
                        cancelToken = cancelToken,
                    )
                } ?: continue
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught")
                failure: Throwable,
            ) {
                // Everything that reaches here is either a non-denial read failure or a local write
                // that failed: both abort the pull rather than reporting a sync that stored nothing.
                log.e(throwable = failure) { "[pull] failed (phase3) collection=$collectionName" }
                raise(failure.toSyncError())
            }
            appliedSummaries += applied
        }

        PullSummary(
            downloadedCount = appliedSummaries.sumOf(PullSummary::downloadedCount),
            conflictCount = appliedSummaries.sumOf(PullSummary::conflictCount),
        )
    }

    /** A user-scoped plugin without a collection has nothing to be pulled from; it is not fatal. */
    private fun pluginsWithCollections(): List<Pair<SyncEntityPlugin, String>> = plugins
        .filter { it.pullScope == PullScope.User }
        .sortedBy { it.pullPriority }
        .mapNotNull { plugin ->
            val collectionName = plugin.firestoreCollectionName
            if (collectionName == null) {
                log.w { "[pull] user-scoped plugin ${plugin.entityType} has no collection — skipping" }
                null
            } else {
                plugin to collectionName
            }
        }

    /**
     * `null` means "skip this collection". A denial is the one failure treated that way: these
     * documents are private to one user and hold nothing another device cannot re-derive, whereas
     * the rules for a brand-new subcollection are exactly the kind of thing that can lag a client
     * release — and failing the pull would turn that into "nobody can sign in".
     */
    @Suppress("TooGenericExceptionCaught") // Classified below; anything but a denial is re-thrown.
    private suspend fun fetch(uid: String, collectionName: String): List<RemoteDocument>? = try {
        reader.fetchAll(uid, collectionName, limit = MAX_DOCS_PER_COLLECTION).also { docs ->
            if (docs.size >= MAX_DOCS_PER_COLLECTION) {
                // Truncation is a bug to report, not a page to fetch: this collection holds one
                // document per settings key and cannot legitimately reach the cap. Applying the
                // prefix is still strictly better than applying nothing.
                log.e {
                    "[pull] users/{uid}/$collectionName hit the $MAX_DOCS_PER_COLLECTION-document " +
                        "cap — the rest of the collection is not being applied"
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        if (!failure.isPermissionDenied()) throw failure
        log.w(throwable = failure) {
            "[pull] users/{uid}/$collectionName is not readable — skipping the collection"
        }
        null
    }

    /** Plugin failures still propagate — those are local writes, and swallowing one would report a
     * clean sync that stored nothing. */
    @Suppress("LongParameterList")
    private suspend fun applyAll(
        plugin: SyncEntityPlugin,
        collectionName: String,
        docs: List<RemoteDocument>,
        scopeKey: String,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): PullSummary {
        var downloaded = 0
        var conflicts = 0

        docs.forEachIndexed { index, doc ->
            cancelToken.throwIfCancelled()
            val result = plugin.applyDoc(doc, scopeKey)
            if (result.applied) downloaded++
            if (result.wasConflict) conflicts++
            onProgress(
                PullProgress(collection = collectionName, current = index + 1, total = docs.size),
            )
        }

        return PullSummary(downloadedCount = downloaded, conflictCount = conflicts)
    }

    private companion object {
        const val TAG = "UserScopedPull"

        /**
         * Ceiling on one cursorless read. Two orders of magnitude above the roughly ten documents a
         * healthy `config` collection holds, so it can only be reached by drift or by a bug —
         * which is exactly when an uncapped read would hurt most, since it runs on every foreground
         * sync on every device.
         */
        const val MAX_DOCS_PER_COLLECTION: Int = 500

        val EMPTY_SUMMARY = PullSummary(downloadedCount = 0, conflictCount = 0)
    }
}
