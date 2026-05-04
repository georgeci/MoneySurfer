package com.georgeci.moneysurfer.sync.api

/**
 * Steps emitted by the coordinator pipeline.
 *
 * `ResolvingConflicts` is intentionally omitted — the pull stage resolves
 * conflicts itself via LWW (see SyncCoordinatorFAQ.md №5).
 */
sealed interface SyncStep {
    val title: String

    data object WaitingForNetwork : SyncStep {
        override val title: String = "Waiting for network"
    }

    data object Started : SyncStep {
        override val title: String = "Sync started"
    }

    data object UploadingPendingChanges : SyncStep {
        override val title: String = "Uploading changes"
    }

    data class UploadingEntity(
        val entityType: String,
        val current: Int,
        val total: Int,
    ) : SyncStep {
        override val title: String = "Uploading $entityType $current/$total"
    }

    data object PullingRemoteChanges : SyncStep {
        override val title: String = "Downloading changes"
    }

    data class PullingCollection(
        val collection: String,
        val current: Int? = null,
        val total: Int? = null,
    ) : SyncStep {
        override val title: String = "Downloading $collection"
    }

    data object RecalculatingProjections : SyncStep {
        override val title: String = "Recalculating balances"
    }

    data class Completed(val summary: SyncSummary) : SyncStep {
        override val title: String = "Sync completed"
    }

    data class Cancelled(val summary: SyncSummary? = null) : SyncStep {
        override val title: String = "Sync cancelled"
    }

    data class Failed(val error: SyncError) : SyncStep {
        override val title: String = "Sync failed"
    }
}
