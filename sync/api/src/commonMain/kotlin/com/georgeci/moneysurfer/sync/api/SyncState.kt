package com.georgeci.moneysurfer.sync.api

/**
 * Global coordinator state — only live activity.
 * Terminal outcomes (success / fail / cancel) live in [LastSyncOutcome].
 * See SyncCoordinatorFAQ.md №10.
 */
sealed interface SyncState {
    data object Idle : SyncState
    data class Queued(val count: Int) : SyncState
    data class Running(
        val requestId: SyncRequestId,
        val reasons: Set<SyncReason>,
        val scope: SyncScope,
        val currentStep: SyncStep,
    ) : SyncState
}
