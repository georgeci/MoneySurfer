package com.georgeci.moneysurfer.sync.api

import com.georgeci.moneysurfer.sync.internal.SyncSummaryBuilder
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncSummaryBuilderSpec : StringSpec({

    "build returns zeros by default" {
        SyncSummaryBuilder().build() shouldBe SyncSummary()
    }

    "addUpload accumulates uploadedCount" {
        val builder = SyncSummaryBuilder()
        builder.addUpload(3)
        builder.addUpload(2)
        builder.build() shouldBe SyncSummary(uploadedCount = 5)
    }

    "addDownload accumulates downloadedCount" {
        val builder = SyncSummaryBuilder()
        builder.addDownload(7)
        builder.addDownload(1)
        builder.build() shouldBe SyncSummary(downloadedCount = 8)
    }

    "addConflicts accumulates conflictCount" {
        val builder = SyncSummaryBuilder()
        builder.addConflicts(4)
        builder.addConflicts(6)
        builder.build() shouldBe SyncSummary(conflictCount = 10)
    }

    "addRecalculated accumulates recalculatedCount" {
        val builder = SyncSummaryBuilder()
        builder.addRecalculated(2)
        builder.addRecalculated(3)
        builder.build() shouldBe SyncSummary(recalculatedCount = 5)
    }

    "all counters compose into one summary" {
        val builder = SyncSummaryBuilder()
        builder.addUpload(1)
        builder.addDownload(2)
        builder.addConflicts(3)
        builder.addRecalculated(4)
        builder.build() shouldBe SyncSummary(
            uploadedCount = 1,
            downloadedCount = 2,
            conflictCount = 3,
            recalculatedCount = 4,
        )
    }
})
