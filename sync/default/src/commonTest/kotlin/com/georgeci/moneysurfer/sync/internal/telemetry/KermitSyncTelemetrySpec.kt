package com.georgeci.moneysurfer.sync.internal.telemetry

import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import io.kotest.core.spec.style.StringSpec
import kotlin.time.Duration.Companion.seconds

/**
 * Kermit logger has no in-memory test sink in commonMain; we exercise every
 * lambda branch end-to-end so a malformed format string would throw and fail
 * the test. The actual output is observed via Kermit's default platform sink.
 */
class KermitSyncTelemetrySpec : StringSpec({

    val telemetry = KermitSyncTelemetry()
    val id = SyncRequestId("k-1")

    "onSyncStarted runs without error" {
        telemetry.onSyncStarted(id, setOf(SyncReason.MANUAL, SyncReason.FOREGROUND), SyncScope.AllUserData)
    }

    "onStepEntered handles every SyncStep variant including parameterised ones" {
        val steps = listOf(
            SyncStep.WaitingForNetwork,
            SyncStep.Started,
            SyncStep.UploadingPendingChanges,
            SyncStep.UploadingEntity("TRANSACTION", 1, 5),
            SyncStep.PullingRemoteChanges,
            SyncStep.PullingCollection("accounts", 0, 10),
            SyncStep.RecalculatingProjections,
            SyncStep.Completed(SyncSummary()),
            SyncStep.Cancelled(),
            SyncStep.Failed(SyncError.NetworkUnavailable),
        )
        steps.forEach { telemetry.onStepEntered(id, it) }
    }

    "onSyncCompleted formats counts" {
        telemetry.onSyncCompleted(
            id,
            SyncSummary(
                uploadedCount = 3,
                downloadedCount = 7,
                conflictCount = 1,
                recalculatedCount = 2,
            ),
            5.seconds,
        )
    }

    "onSyncFailed branches across every SyncError variant" {
        val errors = listOf(
            SyncError.Cancelled,
            SyncError.NetworkUnavailable,
            SyncError.AuthRequired,
            SyncError.PermissionDenied,
            SyncError.UnsupportedAppVersion("update needed"),
            SyncError.StorageError(IllegalStateException("disk")),
            SyncError.Unknown(RuntimeException("oops")),
        )
        errors.forEach { telemetry.onSyncFailed(id, it, 1.seconds) }
    }

    "onSyncCancelled runs without error" {
        telemetry.onSyncCancelled(id, 2.seconds)
    }
})
