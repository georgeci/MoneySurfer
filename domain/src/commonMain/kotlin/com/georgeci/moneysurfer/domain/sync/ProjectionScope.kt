package com.georgeci.moneysurfer.domain.sync

/**
 * Scope of a recalculation request.
 *
 * Decoupled from `SyncScope` because local user mutations also trigger
 * recalculation outside of the sync pipeline.
 * See SyncCoordinatorFAQ.md №6.
 */
sealed interface ProjectionScope {
    /** Recalculate everything for the workspace identified by [key]. */
    data class Workspace(val key: String) : ProjectionScope
    data object All : ProjectionScope
}
