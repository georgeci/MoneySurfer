package com.georgeci.moneysurfer.sync.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class SyncStepSpec : StringSpec({

    "WaitingForNetwork title" {
        SyncStep.WaitingForNetwork.title shouldBe "Waiting for network"
    }

    "Started title" {
        SyncStep.Started.title shouldBe "Sync started"
    }

    "UploadingPendingChanges title" {
        SyncStep.UploadingPendingChanges.title shouldBe "Uploading changes"
    }

    "UploadingEntity title carries entity, current, total" {
        val step = SyncStep.UploadingEntity(
            entityType = "TRANSACTION",
            current = 7,
            total = 10,
        )
        step.title shouldContain "TRANSACTION"
        step.title shouldContain "7/10"
    }

    "PullingRemoteChanges title" {
        SyncStep.PullingRemoteChanges.title shouldBe "Downloading changes"
    }

    "PullingCollection title carries collection name" {
        SyncStep.PullingCollection("accounts").title shouldContain "accounts"
    }

    "PullingCollection defaults current and total to null" {
        val step = SyncStep.PullingCollection("categories")
        step.current shouldBe null
        step.total shouldBe null
    }

    "RecalculatingProjections title" {
        SyncStep.RecalculatingProjections.title shouldBe "Recalculating balances"
    }

    "Completed title" {
        SyncStep.Completed(SyncSummary()).title shouldBe "Sync completed"
    }

    "Cancelled title and default summary" {
        val step = SyncStep.Cancelled()
        step.title shouldBe "Sync cancelled"
        step.summary shouldBe null
    }

    "Failed title carries error" {
        val error = SyncError.NetworkUnavailable
        val step = SyncStep.Failed(error)
        step.title shouldBe "Sync failed"
        step.error shouldBe error
    }
})
