package com.georgeci.moneysurfer.data.sync

import arrow.core.raise.Raise
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullRemoteChangesUseCase
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.PullScope
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.SyncMetaRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Cursor-based incremental pull. For each workspace in scope and each plugin that exposes a
 * [SyncEntityPlugin.firestoreCollectionName]:
 * 1. read cursor from [SyncMetaRepository]
 * 2. query `WHERE updatedAt > cursor ORDER BY updatedAt LIMIT BATCH` via [WorkspaceCollectionReader]
 * 3. dispatch each doc to the plugin's [SyncEntityPlugin.applyDoc]
 * 4. advance the cursor to `max(updatedAt)` of the batch
 * 5. repeat from (2) while the batch came back full, so one pull drains the collection instead
 *    of leaving the tail to the next sync cycle
 *
 * Scope determines which workspaces are pulled:
 * - [SyncScope.ActiveWorkspace] → current workspace only (`session.currentWorkspaceId`)
 * - [SyncScope.AllUserData] / [SyncScope.ChangedSinceLastSync] → all workspaces from
 *   [UserWorkspacesProvider] (fetches `users/{uid}.workspaceIds` from remote)
 * - [SyncScope.UploadOnly] → no pull
 *
 * Members and invites are pulled first so membership rows exist before
 * queries that gate on them.
 *
 * Phase 3 (every scope that pulls at all): collections under `users/{uid}` rather than under a
 * workspace — the per-user settings. Cursorless and read whole; it lives in [UserScopedPullPhase],
 * which shares no state with the workspace phases.
 *
 * Phase 2 ([SyncScope.AllUserData] / [SyncScope.ChangedSinceLastSync] only): pulls the
 * `invites` collection from workspaces listed in `users/{uid}.invitedWorkspaceIds` — workspaces
 * the user has been invited to but has not yet joined. This replaces the previous
 * `collectionGroup("invites")` approach in [IncomingInviteRemoteRepository].
 *
 * A workspace whose *remote reads* fail is logged and skipped rather than failing the pull: a
 * `users/{uid}.workspaceIds` entry can point at a workspace this user cannot read (a stale ref
 * left behind by an aborted create), and one such entry used to make sign-in impossible for the
 * whole account (issue #342). Plugin failures are a different matter — those are local writes,
 * and they still abort.
 */
@Single(binds = [PullRemoteChangesUseCase::class])
class PullRemoteChangesUseCaseImpl(
    private val collectionReader: WorkspaceCollectionReader,
    private val userScopedPull: UserScopedPullPhase,
    private val syncMeta: SyncMetaRepository,
    private val plugins: List<SyncEntityPlugin>,
    private val session: SessionPointers,
    private val userWorkspacesProvider: UserWorkspacesProvider,
) : PullRemoteChangesUseCase {

    private val log = Logger.withTag("PullRemoteChanges")

    override suspend fun invoke(
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<PullSummary> = either {
        val memberWorkspaces = pullMemberWorkspaces(
            workspaceIds = workspaceIdsForScope(scope),
            scope = scope,
            onProgress = onProgress,
            cancelToken = cancelToken,
        )
        val invited = pullInvitedWorkspaces(
            scope = scope,
            onProgress = onProgress,
            cancelToken = cancelToken,
        )
        val userScoped = userScopedPull(
            scope = scope,
            onProgress = onProgress,
            cancelToken = cancelToken,
        ).bind()

        PullSummary(
            downloadedCount = memberWorkspaces.downloadedCount +
                invited.downloadedCount +
                userScoped.downloadedCount,
            conflictCount = memberWorkspaces.conflictCount +
                invited.conflictCount +
                userScoped.conflictCount,
        )
    }

    /** Phase 1: every workspace the user is a member of, in [SyncEntityPlugin.pullPriority] order. */
    private suspend fun Raise<SyncError>.pullMemberWorkspaces(
        workspaceIds: List<String>,
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): PullSummary {
        var totalDownloaded = 0
        var totalConflicts = 0

        for (workspaceId in workspaceIds) {
            val summary = pullWorkspace(
                workspaceId = workspaceId,
                scope = scope,
                onProgress = onProgress,
                cancelToken = cancelToken,
            )
            totalDownloaded += summary.downloadedCount
            totalConflicts += summary.conflictCount
        }

        return PullSummary(downloadedCount = totalDownloaded, conflictCount = totalConflicts)
    }

    /**
     * Runs every in-scope plugin against one workspace. Stops early — keeping whatever already
     * landed — when the workspace turns out to be unreadable, so a single stale entry in
     * `users/{uid}.workspaceIds` cannot block sign-in for the whole account.
     */
    private suspend fun Raise<SyncError>.pullWorkspace(
        workspaceId: String,
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): PullSummary {
        val pluginSummaries = mutableListOf<PullSummary>()

        for (plugin in pluginsInScope(scope)) {
            cancelToken.throwIfCancelled()
            val collectionName = plugin.firestoreCollectionName

            val (downloaded, conflicts) = try {
                pullOne(
                    workspaceId = workspaceId,
                    plugin = plugin,
                    onProgress = onProgress,
                    cancelToken = cancelToken,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unreadable: WorkspaceUnreadableException) {
                log.w(throwable = unreadable.cause) {
                    "[pull] wid=$workspaceId unreadable at " +
                        "collection=${collectionName ?: "root"} — treating it as a stale " +
                        "workspaceIds ref and skipping the workspace"
                }
                break
            } catch (
                @Suppress("TooGenericExceptionCaught")
                failure: Throwable,
            ) {
                log.e(throwable = failure) {
                    "[pull] failed entity=${plugin.entityType} " +
                        "collection=${collectionName ?: "root"} wid=$workspaceId"
                }
                raise(failure.toSyncError())
            }
            pluginSummaries += PullSummary(downloadedCount = downloaded, conflictCount = conflicts)
        }

        return PullSummary(
            downloadedCount = pluginSummaries.sumOf(PullSummary::downloadedCount),
            conflictCount = pluginSummaries.sumOf(PullSummary::conflictCount),
        )
    }

    /**
     * One plugin against one workspace. A root-doc plugin
     * ([SyncEntityPlugin.firestoreCollectionName] == null) owns the `workspaces/{wid}` document
     * itself and is applied directly, which is what guarantees the local `WorkspaceEntity` exists
     * before subcollection plugins run — `AccountEntity` / `CategoryEntity` / `TransactionEntity`
     * all carry a Room FK `workspaceId → WorkspaceEntity` (SQLite error 787 otherwise).
     */
    private suspend fun pullOne(
        workspaceId: String,
        plugin: SyncEntityPlugin,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): Pair<Int, Int> {
        val collectionName = plugin.firestoreCollectionName
            ?: run {
                val doc = readRemote(workspaceId) { collectionReader.fetchWorkspaceDoc(workspaceId) }
                val applied = doc != null && plugin.applyDoc(doc, workspaceId).applied
                return if (applied) 1 to 0 else 0 to 0
            }
        return pullCollection(
            workspaceId = workspaceId,
            plugin = plugin,
            collectionName = collectionName,
            onProgress = onProgress,
            cancelToken = cancelToken,
        )
    }

    /**
     * Phase 2: pull the `invites` collection from workspaces the user is invited to but has not
     * yet joined. Only for scopes that do a full user-data fetch.
     *
     * Uses [WorkspaceCollectionReader.fetchInvitesForUser] (which adds a `targetUserId` filter)
     * because the caller is not yet a workspace member and a general list query would be
     * PERMISSION_DENIED.
     */
    private suspend fun Raise<SyncError>.pullInvitedWorkspaces(
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): PullSummary {
        val fullFetch = scope == SyncScope.AllUserData || scope == SyncScope.ChangedSinceLastSync
        val uid = session.currentFirebaseUid.first()
        val invitePlugin = plugins.firstOrNull {
            it.firestoreCollectionName == SyncCollection.WORKSPACE_INVITES
        }
        if (!fullFetch || uid == null || invitePlugin == null) return EMPTY_SUMMARY

        var totalDownloaded = 0
        var totalConflicts = 0

        for (workspaceId in userWorkspacesProvider.invitedWorkspaceIds()) {
            cancelToken.throwIfCancelled()
            val (downloaded, conflicts) = try {
                pullInvitesForUser(
                    workspaceId = workspaceId,
                    uid = uid,
                    plugin = invitePlugin,
                    onProgress = onProgress,
                    cancelToken = cancelToken,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unreadable: WorkspaceUnreadableException) {
                log.w(throwable = unreadable.cause) {
                    "[pull] invited wid=$workspaceId unreadable — skipping (stale invited ref)"
                }
                continue
            } catch (
                @Suppress("TooGenericExceptionCaught")
                failure: Throwable,
            ) {
                log.e(throwable = failure) { "[pull] failed (phase2/invites) wid=$workspaceId" }
                raise(failure.toSyncError())
            }
            totalDownloaded += downloaded
            totalConflicts += conflicts
        }

        return PullSummary(downloadedCount = totalDownloaded, conflictCount = totalConflicts)
    }

    private suspend fun workspaceIdsForScope(scope: SyncScope): List<String> = when (scope) {
        SyncScope.UploadOnly -> emptyList()
        SyncScope.ActiveWorkspace -> {
            session.currentWorkspaceId.first()?.value
                ?.let { listOf(it) } ?: emptyList()
        }
        SyncScope.AllUserData -> userWorkspacesProvider.workspaceIds()
        SyncScope.ChangedSinceLastSync -> {
            val ids = userWorkspacesProvider.workspaceIds()
            ids.ifEmpty {
                // Fallback for incremental sync: provider returned nothing (e.g. first
                // launch before workspaceIds has been pushed to Firestore). Use the
                // locally-known active workspace so at least one workspace syncs.
                // NOT used for AllUserData — a full sync should only pull what the
                // remote says the user owns; using currentWorkspaceId for AllUserData
                // causes PERMISSION_DENIED when the user is only an invitee of that wid.
                session.currentWorkspaceId.first()?.value
                    ?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    private suspend fun pullCollection(
        workspaceId: String,
        plugin: SyncEntityPlugin,
        collectionName: String,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): Pair<Int, Int> = pullBatches(
        workspaceId = workspaceId,
        collectionName = collectionName,
        plugin = plugin,
        onProgress = onProgress,
        cancelToken = cancelToken,
    ) { sinceMillis ->
        readRemote(workspaceId) {
            collectionReader.fetchUpdatedSince(
                workspaceId = workspaceId,
                collectionName = collectionName,
                sinceMillis = sinceMillis,
                limit = BATCH_SIZE,
            )
        }
    }

    /**
     * Pulls invites from a workspace the caller is invited to but has not joined.
     * Uses [WorkspaceCollectionReader.fetchInvitesForUser] which adds a `targetUserId == uid`
     * filter, making the query satisfy Firestore rules for non-members.
     */
    private suspend fun pullInvitesForUser(
        workspaceId: String,
        uid: String,
        plugin: SyncEntityPlugin,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): Pair<Int, Int> = pullBatches(
        workspaceId = workspaceId,
        collectionName = SyncCollection.WORKSPACE_INVITES,
        plugin = plugin,
        onProgress = onProgress,
        cancelToken = cancelToken,
    ) { sinceMillis ->
        readRemote(workspaceId) {
            collectionReader.fetchInvitesForUser(
                workspaceId = workspaceId,
                uid = uid,
                sinceMillis = sinceMillis,
                limit = BATCH_SIZE,
            )
        }
    }

    /**
     * Cursor-based paged pull shared by [pullCollection] and [pullInvitesForUser]: reads the
     * cursor, then repeatedly runs [fetch] for everything updated since it, applies each doc via
     * the [plugin] and advances the cursor to `max(updatedAt)` — until a batch comes back short
     * of [BATCH_SIZE]. Only the query differs between callers, so it is supplied as [fetch].
     *
     * Draining here rather than leaving the tail to the next sync cycle is what makes a fresh
     * sign-in show correct balances: the background ticker only fires once a minute, so a
     * single-batch pull left an account with a few thousand transactions wrong for tens of
     * minutes (issue #342).
     *
     * Two independent stops guard against an endless loop: [MAX_BATCHES_PER_COLLECTION], and a
     * cursor that failed to advance — which happens when more than [BATCH_SIZE] documents share
     * one `updatedAt` millisecond, since the query is a strict `>` on the cursor.
     *
     * @return `downloaded to conflicts` across every batch.
     */
    @Suppress("LongParameterList")
    private suspend fun pullBatches(
        workspaceId: String,
        collectionName: String,
        plugin: SyncEntityPlugin,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
        fetch: suspend (sinceMillis: Long) -> List<RemoteDocument>,
    ): Pair<Int, Int> {
        syncMeta.markAttempt(workspaceId, collectionName, Clock.System.now())

        var cursorMillis = syncMeta.cursor(workspaceId, collectionName)?.toEpochMilliseconds() ?: 0L
        var downloaded = 0
        var conflicts = 0
        var batches = 0
        var draining = true

        while (draining) {
            cancelToken.throwIfCancelled()
            val docs = fetch(cursorMillis)
            val batch = applyBatch(
                workspaceId = workspaceId,
                collectionName = collectionName,
                plugin = plugin,
                docs = docs,
                cursorMillis = cursorMillis,
                onProgress = onProgress,
                cancelToken = cancelToken,
            )
            downloaded += batch.downloaded
            conflicts += batch.conflicts
            batches++

            val cursorAdvanced = batch.maxUpdatedAt > cursorMillis
            if (cursorAdvanced) {
                cursorMillis = batch.maxUpdatedAt
                syncMeta.setCursor(
                    scopeKey = workspaceId,
                    collection = collectionName,
                    cursor = Instant.fromEpochMilliseconds(cursorMillis),
                )
            }

            val full = docs.size == BATCH_SIZE
            draining = full && cursorAdvanced && batches < MAX_BATCHES_PER_COLLECTION
            logStopReason(workspaceId, collectionName, docs.size, full, cursorAdvanced, batches)
        }

        syncMeta.markSuccess(workspaceId, collectionName, Clock.System.now())

        return downloaded to conflicts
    }

    /** Applies one batch and reports what it did, including how far the cursor can move. */
    @Suppress("LongParameterList")
    private suspend fun applyBatch(
        workspaceId: String,
        collectionName: String,
        plugin: SyncEntityPlugin,
        docs: List<RemoteDocument>,
        cursorMillis: Long,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): BatchOutcome {
        var downloaded = 0
        var conflicts = 0
        var maxUpdatedAt = cursorMillis

        docs.forEachIndexed { index, doc ->
            cancelToken.throwIfCancelled()
            val result: EntityApplyResult = plugin.applyDoc(doc, workspaceId)
            if (result.applied) downloaded++
            if (result.wasConflict) conflicts++

            val docUpdatedAt = doc.getLong("updatedAt") ?: 0L
            if (docUpdatedAt > maxUpdatedAt) maxUpdatedAt = docUpdatedAt

            onProgress(
                PullProgress(collection = collectionName, current = index + 1, total = docs.size),
            )
        }

        return BatchOutcome(downloaded = downloaded, conflicts = conflicts, maxUpdatedAt = maxUpdatedAt)
    }

    /** Notes the two ways a full batch can stop the drain early; a short batch is the normal end. */
    @Suppress("LongParameterList")
    private fun logStopReason(
        workspaceId: String,
        collectionName: String,
        batchSize: Int,
        full: Boolean,
        cursorAdvanced: Boolean,
        batches: Int,
    ) {
        if (full && !cursorAdvanced) {
            log.w {
                "[pull] cursor stuck for wid=$workspaceId collection=$collectionName — all " +
                    "$batchSize docs in the batch carry the same (or no) updatedAt; stopping to " +
                    "avoid re-reading the same batch forever"
            }
        }
        if (full && cursorAdvanced && batches >= MAX_BATCHES_PER_COLLECTION) {
            log.w {
                "[pull] hit the $MAX_BATCHES_PER_COLLECTION-batch ceiling for wid=$workspaceId " +
                    "collection=$collectionName — the rest is left to the next sync, which " +
                    "resumes from the stored cursor"
            }
        }
    }

    /**
     * Runs a remote read. A **denial** is re-thrown as [WorkspaceUnreadableException] so callers
     * can skip the workspace; everything else propagates untouched and still aborts the pull.
     *
     * The distinction matters: a denied read means this workspace will never be readable for this
     * user, so skipping it is the whole point. A network drop or an expired token means the data
     * is simply not here *yet* — swallowing those would let the pull report a successful sync that
     * downloaded nothing, and the UI would tell the user they are up to date when they are not.
     */
    private suspend fun <T> readRemote(workspaceId: String, read: suspend () -> T): T = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught")
        failure: Throwable,
    ) {
        if (failure.isPermissionDenied()) {
            throw WorkspaceUnreadableException(workspaceId, failure)
        }
        throw failure
    }

    /**
     * Returns the workspace-scoped plugins in pull order ([SyncEntityPlugin.pullPriority], lower
     * first).
     *
     * Includes plugins with [SyncEntityPlugin.firestoreCollectionName] == null — those
     * own workspace root documents and are dispatched via
     * [WorkspaceCollectionReader.fetchWorkspaceDoc] rather than [pullCollection].
     * See [SyncEntityPlugin] kdoc for tier assignments.
     *
     * [PullScope.User] plugins are filtered out here and run once in phase 3 instead: this loop
     * runs per workspace, and their documents do not live under a workspace at all.
     */
    private fun pluginsInScope(scope: SyncScope): List<SyncEntityPlugin> {
        if (scope == SyncScope.UploadOnly) return emptyList()
        return plugins
            .filter { it.pullScope == PullScope.Workspace }
            .sortedBy { it.pullPriority }
    }

    private companion object {
        const val BATCH_SIZE: Int = 100

        /**
         * Ceiling on batches per collection per workspace per sync — 50 × 100 = 5 000 documents.
         * Not a correctness bound (the cursor is persisted, so the next sync resumes where this
         * one stopped); it just keeps one pathological collection from monopolising a sync.
         */
        const val MAX_BATCHES_PER_COLLECTION: Int = 50

        val EMPTY_SUMMARY = PullSummary(downloadedCount = 0, conflictCount = 0)
    }
}

/** What one batch applied, and the newest `updatedAt` it saw. */
private data class BatchOutcome(
    val downloaded: Int,
    val conflicts: Int,
    val maxUpdatedAt: Long,
)

/**
 * A workspace whose remote documents could not be read — typically PERMISSION_DENIED on a
 * `users/{uid}.workspaceIds` entry whose `workspaces/{wid}` document was never created. Private
 * to the pull: it never escapes [PullRemoteChangesUseCaseImpl].
 */
private class WorkspaceUnreadableException(
    workspaceId: String,
    override val cause: Throwable,
) : RuntimeException("Workspace $workspaceId is not readable", cause)
