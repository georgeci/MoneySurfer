package com.georgeci.moneysurfer.data.sync

import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullRemoteChangesUseCase
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
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
 * Phase 2 ([SyncScope.AllUserData] / [SyncScope.ChangedSinceLastSync] only): pulls the
 * `invites` collection from workspaces listed in `users/{uid}.invitedWorkspaceIds` — workspaces
 * the user has been invited to but has not yet joined. This replaces the previous
 * `collectionGroup("invites")` approach in [IncomingInviteRemoteRepository].
 */
@Single(binds = [PullRemoteChangesUseCase::class])
class PullRemoteChangesUseCaseImpl(
    private val collectionReader: WorkspaceCollectionReader,
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
        val workspaceIds = workspaceIdsForScope(scope)

        var totalDownloaded = 0
        var totalConflicts = 0

        for (workspaceId in workspaceIds) {
            for (plugin in pluginsInScope(scope)) {
                cancelToken.throwIfCancelled()
                val collectionName = plugin.firestoreCollectionName

                val (downloaded, conflicts) = try {
                    if (collectionName == null) {
                        // Root-doc plugin (firestoreCollectionName == null): fetch the
                        // workspace root document and apply it directly. This ensures the
                        // local WorkspaceEntity exists before subcollection plugins run —
                        // AccountEntity / CategoryEntity / TransactionEntity all carry a
                        // Room FK workspaceId → WorkspaceEntity (SQLite error 787 otherwise).
                        val doc = collectionReader.fetchWorkspaceDoc(workspaceId)
                        val applied = doc != null && plugin.applyDoc(doc, workspaceId).applied
                        if (applied) 1 to 0 else 0 to 0
                    } else {
                        pullCollection(
                            workspaceId = workspaceId,
                            plugin = plugin,
                            collectionName = collectionName,
                            onProgress = onProgress,
                            cancelToken = cancelToken,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
                totalDownloaded += downloaded
                totalConflicts += conflicts
            }
        }

        // Phase 2: pull `invites` collection from workspaces the user is invited to but
        // has not yet joined. Only for scopes that do a full user-data fetch.
        // Uses fetchInvitesForUser (which adds a targetUserId filter) because the caller is
        // not yet a workspace member and a general list query would be PERMISSION_DENIED.
        if (scope == SyncScope.AllUserData || scope == SyncScope.ChangedSinceLastSync) {
            val uid = session.currentFirebaseUid.flow.first()
            if (uid != null) {
                val invitedIds = userWorkspacesProvider.invitedWorkspaceIds()
                val invitePlugin = plugins.firstOrNull {
                    it.firestoreCollectionName == SyncCollection.WORKSPACE_INVITES
                }
                if (invitePlugin != null) {
                    for (workspaceId in invitedIds) {
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
                        } catch (
                            @Suppress("TooGenericExceptionCaught")
                            failure: Throwable,
                        ) {
                            log.e(throwable = failure) {
                                "[pull] failed (phase2/invites) wid=$workspaceId"
                            }
                            raise(failure.toSyncError())
                        }
                        totalDownloaded += downloaded
                        totalConflicts += conflicts
                    }
                }
            }
        }

        PullSummary(downloadedCount = totalDownloaded, conflictCount = totalConflicts)
    }

    private suspend fun workspaceIdsForScope(scope: SyncScope): List<String> = when (scope) {
        SyncScope.UploadOnly -> emptyList()
        SyncScope.ActiveWorkspace -> {
            session.currentWorkspaceId.flow.first()?.value
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
                session.currentWorkspaceId.flow.first()?.value
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
    ): Pair<Int, Int> = pullBatch(
        workspaceId = workspaceId,
        collectionName = collectionName,
        plugin = plugin,
        onProgress = onProgress,
        cancelToken = cancelToken,
    ) { sinceMillis ->
        collectionReader.fetchUpdatedSince(
            workspaceId = workspaceId,
            collectionName = collectionName,
            sinceMillis = sinceMillis,
            limit = BATCH_SIZE,
        )
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
    ): Pair<Int, Int> = pullBatch(
        workspaceId = workspaceId,
        collectionName = SyncCollection.WORKSPACE_INVITES,
        plugin = plugin,
        onProgress = onProgress,
        cancelToken = cancelToken,
    ) { sinceMillis ->
        collectionReader.fetchInvitesForUser(
            workspaceId = workspaceId,
            uid = uid,
            sinceMillis = sinceMillis,
            limit = BATCH_SIZE,
        )
    }

    /**
     * Cursor-based batch pull shared by [pullCollection] and [pullInvitesForUser]: reads the
     * cursor, runs [fetch] for everything updated since it, applies each doc via the [plugin],
     * advances the cursor to `max(updatedAt)`, and records the attempt/success on [syncMeta].
     * Only the query differs between callers, so it is supplied as [fetch].
     *
     * @return `downloaded to conflicts` for the batch.
     */
    private suspend fun pullBatch(
        workspaceId: String,
        collectionName: String,
        plugin: SyncEntityPlugin,
        onProgress: suspend (PullProgress) -> Unit,
        cancelToken: SyncCancelToken,
        fetch: suspend (sinceMillis: Long) -> List<RemoteDocument>,
    ): Pair<Int, Int> {
        syncMeta.markAttempt(workspaceId, collectionName, Clock.System.now())

        val cursor = syncMeta.cursor(workspaceId, collectionName)
        val cursorMillis = cursor?.toEpochMilliseconds() ?: 0L

        val docs = fetch(cursorMillis)

        if (docs.isEmpty()) {
            syncMeta.markSuccess(workspaceId, collectionName, Clock.System.now())
            return 0 to 0
        }

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
                PullProgress(
                    collection = collectionName,
                    current = index + 1,
                    total = docs.size,
                ),
            )
        }

        if (maxUpdatedAt > cursorMillis) {
            syncMeta.setCursor(
                scopeKey = workspaceId,
                collection = collectionName,
                cursor = Instant.fromEpochMilliseconds(maxUpdatedAt),
            )
        }
        syncMeta.markSuccess(workspaceId, collectionName, Clock.System.now())

        return downloaded to conflicts
    }

    /**
     * Returns all plugins in pull order ([SyncEntityPlugin.pullPriority], lower first).
     *
     * Includes plugins with [SyncEntityPlugin.firestoreCollectionName] == null — those
     * own workspace root documents and are dispatched via
     * [WorkspaceCollectionReader.fetchWorkspaceDoc] rather than [pullCollection].
     * See [SyncEntityPlugin] kdoc for tier assignments.
     */
    private fun pluginsInScope(scope: SyncScope): List<SyncEntityPlugin> {
        if (scope == SyncScope.UploadOnly) return emptyList()
        return plugins.sortedBy { it.pullPriority }
    }

    private companion object {
        const val BATCH_SIZE: Int = 100
    }
}
