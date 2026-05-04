package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Convenience wrapper for repositories: guards against demo mode and
 * packages the [PendingMutation] for the outbox.
 *
 * [entityType] is a string constant from [com.georgeci.moneysurfer.domain.sync.SyncEntityTypes].
 * [scopeKey] carries the workspace id for subcollection entities, null for root-collection entities.
 *
 * The push worker reads the entity from Room at push time — no payload serialization happens here.
 * See sync.md §4.3.
 */
@Single(binds = [OutboxEnqueuer::class])
class OutboxEnqueuerImpl(
    private val outbox: PendingMutationQueue,
    private val session: SessionPointers,
    private val appVersionGate: AppVersionGate,
) : OutboxEnqueuer {

    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) {
        require(operation != MutationOperation.DELETE) {
            "DELETE has no payload — call enqueueDelete instead"
        }
        if (!isEnabled()) return
        outbox.enqueue(buildMutation(entityType, entityId, scopeKey, operation))
    }

    override suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
        scopeKey: String?,
    ) {
        if (!isEnabled()) return
        outbox.enqueue(buildMutation(entityType, entityId, scopeKey, MutationOperation.DELETE))
    }

    /**
     * Outbox accepts mutations only when:
     * 1. The user is signed in to a real Firebase account (demo sessions
     *    skip — mutations stay purely local).
     * 2. The remote app-version gate is not in `Unsupported` state.
     *    See block.md.
     */
    override suspend fun isEnabled(): Boolean {
        val uid = session.currentFirebaseUid.flow.first()
        if (uid.isNullOrEmpty()) return false
        return appVersionGate.isSyncAllowed()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun buildMutation(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ): PendingMutation = PendingMutation(
        id = Uuid.random().toString(),
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        scopeKey = scopeKey,
        createdAt = Clock.System.now(),
        attempts = 0,
        lastError = null,
    )
}
