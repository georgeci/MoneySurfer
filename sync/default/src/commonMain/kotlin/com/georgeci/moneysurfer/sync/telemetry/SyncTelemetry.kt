package com.georgeci.moneysurfer.sync.telemetry

import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import kotlin.time.Duration

/**
 * Hook for emitting structured sync events. Default binding is [NoOp];
 * `:data` provides a Kermit-backed implementation that logs human-readable
 * lines. Replace with a metrics-pipeline binding (Firebase Analytics,
 * Crashlytics breadcrumbs, or a custom collector) when one lands.
 *
 * See sync.md §4.2.
 */
interface SyncTelemetry {

    fun onSyncStarted(requestId: SyncRequestId, reasons: Set<SyncReason>, scope: SyncScope)

    fun onStepEntered(requestId: SyncRequestId, step: SyncStep)

    fun onSyncCompleted(requestId: SyncRequestId, summary: SyncSummary, duration: Duration)

    fun onSyncFailed(requestId: SyncRequestId, error: SyncError, duration: Duration)

    fun onSyncCancelled(requestId: SyncRequestId, duration: Duration)

    object NoOp : SyncTelemetry {
        override fun onSyncStarted(requestId: SyncRequestId, reasons: Set<SyncReason>, scope: SyncScope) = Unit
        override fun onStepEntered(requestId: SyncRequestId, step: SyncStep) = Unit
        override fun onSyncCompleted(requestId: SyncRequestId, summary: SyncSummary, duration: Duration) = Unit
        override fun onSyncFailed(requestId: SyncRequestId, error: SyncError, duration: Duration) = Unit
        override fun onSyncCancelled(requestId: SyncRequestId, duration: Duration) = Unit
    }
}
