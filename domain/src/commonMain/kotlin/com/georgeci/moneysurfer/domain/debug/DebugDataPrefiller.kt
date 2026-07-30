package com.georgeci.moneysurfer.domain.debug

import arrow.core.Either

/**
 * Fills the pinned workspace with a realistic dataset, so a freshly installed debug build has
 * something to render. Driven by one row in the QA configuration panel, which is itself only
 * reachable while a real Debug configuration layer is bound — i.e. never in a release build.
 *
 * Session-agnostic on purpose. Everything is written through the ordinary repositories, so the
 * rows enqueue on the outbox exactly like user-entered ones:
 *  - guest ("demo") session — no Firebase uid, so the outbox drains nowhere and the data stays
 *    local, matching the demo-isolation rule in `docs/architecture/sync.md` §2.11;
 *  - signed-in session — the same rows reach Firestore on the next drain, which [prefill] asks
 *    for itself.
 *
 * Re-running adds another batch of transactions. Accounts, budgets and goals are matched by
 * name first, so only the transaction count grows.
 */
fun interface DebugDataPrefiller {

    suspend fun prefill(): Either<DebugPrefillError, DebugPrefillReport>
}

/** What one [DebugDataPrefiller.prefill] run actually wrote. Every count is rows *created*. */
data class DebugPrefillReport(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val budgets: Int,
    val goals: Int,
)

sealed interface DebugPrefillError {

    /**
     * No workspace is pinned — the session has not finished bootstrapping, or the user is on the
     * sign-in screen. There is nothing to attach rows to, so the run is refused rather than
     * inventing a workspace behind the user's back.
     */
    data object NoWorkspace : DebugPrefillError

    data class WriteFailed(val cause: Throwable) : DebugPrefillError
}
