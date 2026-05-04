package com.georgeci.moneysurfer.sync.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class SyncStateSpec : StringSpec({

    "Idle is a singleton object" {
        (SyncState.Idle as SyncState) shouldBe SyncState.Idle
    }

    "Queued carries count" {
        SyncState.Queued(3).count shouldBe 3
    }

    "Running carries id, reasons, scope, currentStep" {
        val id = SyncRequestId("r-1")
        val state = SyncState.Running(
            requestId = id,
            reasons = setOf(SyncReason.MANUAL),
            scope = SyncScope.AllUserData,
            currentStep = SyncStep.Started,
        )
        state.requestId shouldBe id
        state.reasons shouldBe setOf(SyncReason.MANUAL)
        state.scope shouldBe SyncScope.AllUserData
        state.currentStep shouldBe SyncStep.Started
    }

    "SyncHandleStatus.Queued is singleton" {
        (SyncHandleStatus.Queued as SyncHandleStatus) shouldBe SyncHandleStatus.Queued
    }

    "SyncHandleStatus.Running carries currentStep" {
        SyncHandleStatus.Running(SyncStep.Started).currentStep shouldBe SyncStep.Started
    }

    "SyncHandleStatus.Completed carries summary" {
        val summary = SyncSummary(uploadedCount = 1)
        SyncHandleStatus.Completed(summary).summary shouldBe summary
    }

    "SyncHandleStatus.Failed carries error" {
        val error = SyncError.NetworkUnavailable
        SyncHandleStatus.Failed(error).error shouldBe error
    }

    "SyncHandleStatus.Cancelled is singleton" {
        (SyncHandleStatus.Cancelled as SyncHandleStatus) shouldBe SyncHandleStatus.Cancelled
    }

    "LastSyncOutcome.None is singleton" {
        (LastSyncOutcome.None as LastSyncOutcome) shouldBe LastSyncOutcome.None
    }

    "LastSyncOutcome.Success carries summary and timestamp" {
        val summary = SyncSummary(uploadedCount = 2, downloadedCount = 5)
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val outcome = LastSyncOutcome.Success(summary, at)
        outcome.summary shouldBe summary
        outcome.at shouldBe at
    }

    "LastSyncOutcome.Failed carries error and timestamp" {
        val error = SyncError.PermissionDenied
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val outcome = LastSyncOutcome.Failed(error, at)
        outcome.error shouldBe error
        outcome.at shouldBe at
    }

    "LastSyncOutcome.Cancelled carries timestamp" {
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        LastSyncOutcome.Cancelled(at).at shouldBe at
    }
})
