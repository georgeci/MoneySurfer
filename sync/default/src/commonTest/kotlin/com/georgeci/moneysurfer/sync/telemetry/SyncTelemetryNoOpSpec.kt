package com.georgeci.moneysurfer.sync.telemetry

import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import io.kotest.core.spec.style.StringSpec
import kotlin.time.Duration.Companion.seconds

/**
 * The default `NoOp` binding must be safely callable on every hook with any
 * argument shape — coordinator code calls it unconditionally.
 */
class SyncTelemetryNoOpSpec : StringSpec({

    val id = SyncRequestId("noop-1")

    "onSyncStarted is callable" {
        SyncTelemetry.NoOp.onSyncStarted(
            requestId = id,
            reasons = setOf(SyncReason.MANUAL),
            scope = SyncScope.AllUserData,
        )
    }

    "onStepEntered is callable" {
        SyncTelemetry.NoOp.onStepEntered(id, SyncStep.Started)
    }

    "onSyncCompleted is callable" {
        SyncTelemetry.NoOp.onSyncCompleted(id, SyncSummary(), 1.seconds)
    }

    "onSyncFailed is callable" {
        SyncTelemetry.NoOp.onSyncFailed(id, SyncError.NetworkUnavailable, 2.seconds)
    }

    "onSyncCancelled is callable" {
        SyncTelemetry.NoOp.onSyncCancelled(id, 3.seconds)
    }
})
