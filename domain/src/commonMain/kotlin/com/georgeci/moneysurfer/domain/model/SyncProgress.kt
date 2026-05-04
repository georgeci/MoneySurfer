package com.georgeci.moneysurfer.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

/**
 * Per-step progress emitted by [com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncRepository].
 * The flow emits [InProgress] before each network/db step starts, and a single terminal
 * [Completed] carrying the aggregated [SyncReport]. Errors are propagated as flow exceptions.
 */
sealed interface SyncProgress {
    data class InProgress(val step: SyncStep) : SyncProgress
    data class Completed(val report: SyncReport) : SyncProgress
}

enum class SyncStep {
    PUSH_WORKSPACE,

    /**
     * Pushes `/workspaces/{wid}/members/{uid}` rows. Must run BEFORE any
     * sub-collection write — Firestore rules gate sub-collection writes on
     * `isMember(wid)`, which reads the members doc. Without this step the
     * first `pushAccounts` after a fresh workspace creation fails with
     * `PERMISSION_DENIED`.
     */
    PUSH_MEMBERS,

    /** Pushes `/workspaces/{wid}/invites/{id}` rows after members so the roster sees them. */
    PUSH_INVITES,
    PUSH_ACCOUNTS,
    PUSH_CATEGORIES,
    PUSH_TRANSACTIONS,
    PULL_WORKSPACE,

    /** Mirror of [PUSH_MEMBERS] on the read side — pulls remote membership rows. */
    PULL_MEMBERS,

    /** Mirror of [PUSH_INVITES] on the read side. */
    PULL_INVITES,
    PULL_ACCOUNTS,
    PULL_CATEGORIES,
    PULL_TRANSACTIONS,
}

suspend fun Flow<SyncProgress>.awaitReport(): SyncReport =
    filterIsInstance<SyncProgress.Completed>().first().report
