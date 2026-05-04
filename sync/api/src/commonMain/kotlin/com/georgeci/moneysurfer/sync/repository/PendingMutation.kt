package com.georgeci.moneysurfer.sync.repository

import kotlin.time.Instant

/**
 * One queued local mutation awaiting push to the remote backend.
 *
 * Outbox row identity is [id] (UUID), independent from the entity's own id —
 * the same entity may have multiple sequential mutations.
 *
 * [entityType] is an opaque string (e.g. "ACCOUNT", "TRANSACTION") defined as
 * constants in [com.georgeci.moneysurfer.domain.sync.SyncEntityTypes].
 * [scopeKey] carries the workspace id for subcollection entities, or null for
 * root-collection entities (e.g. workspace itself, users).
 *
 * The push worker reads the entity from Room at push time — no payload is stored
 * in the outbox. See sync.md §4.3 and SyncCoordinatorFAQ.md №13.
 */
data class PendingMutation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: MutationOperation,
    val scopeKey: String?,
    val createdAt: Instant,
    val attempts: Int,
    val lastError: String?,
)
