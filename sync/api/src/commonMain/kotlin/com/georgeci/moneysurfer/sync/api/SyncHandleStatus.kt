package com.georgeci.moneysurfer.sync.api

sealed interface SyncHandleStatus {
    data object Queued : SyncHandleStatus
    data class Running(val currentStep: SyncStep) : SyncHandleStatus
    data class Completed(val summary: SyncSummary) : SyncHandleStatus
    data class Failed(val error: SyncError) : SyncHandleStatus
    data object Cancelled : SyncHandleStatus
}
