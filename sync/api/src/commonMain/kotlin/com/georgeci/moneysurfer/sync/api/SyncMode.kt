package com.georgeci.moneysurfer.sync.api

/**
 * `ForceAfterCurrent` is intentionally omitted — see SyncCoordinatorFAQ.md №8.
 * When needed, it will be implemented via a real queue, not as an alias of `Enqueue`.
 */
enum class SyncMode {
    Enqueue,
    ReplaceQueued,
}
