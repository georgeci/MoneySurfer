package com.georgeci.moneysurfer.sync.internal.telemetry

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.telemetry.SyncTelemetry
import org.koin.core.annotation.Single
import kotlin.time.Duration

/**
 * Structured logging via Kermit. Tag `SyncTelemetry`, prefix
 * `[req=…]` so a single sync run can be grep'd across noisy logs.
 *
 * Step-level events go to debug; lifecycle (started / completed / failed /
 * cancelled) goes to info. Failure carries the typed [SyncError] for grepping
 * by class.
 */
@Single(binds = [SyncTelemetry::class])
class KermitSyncTelemetry : SyncTelemetry {

    private val log = Logger.withTag(TAG)

    override fun onSyncStarted(
        requestId: SyncRequestId,
        reasons: Set<SyncReason>,
        scope: SyncScope,
    ) {
        log.i { "[req=${requestId.value}] started reasons=$reasons scope=$scope" }
    }

    override fun onStepEntered(requestId: SyncRequestId, step: SyncStep) {
        log.d { "[req=${requestId.value}] step=${step::class.simpleName}" }
    }

    override fun onSyncCompleted(
        requestId: SyncRequestId,
        summary: SyncSummary,
        duration: Duration,
    ) {
        log.i {
            "[req=${requestId.value}] completed in $duration " +
                "uploaded=${summary.uploadedCount} downloaded=${summary.downloadedCount} " +
                "conflicts=${summary.conflictCount} recalculated=${summary.recalculatedCount}"
        }
    }

    override fun onSyncFailed(requestId: SyncRequestId, error: SyncError, duration: Duration) {
        log.w {
            "[req=${requestId.value}] failed in $duration " +
                "error=${error::class.simpleName} retryable=${error.isRetryable}"
        }
    }

    override fun onSyncCancelled(requestId: SyncRequestId, duration: Duration) {
        log.i { "[req=${requestId.value}] cancelled after $duration" }
    }

    private companion object {
        const val TAG = "SyncTelemetry"
    }
}
