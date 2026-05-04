package com.georgeci.moneysurfer.sync.internal

import com.georgeci.moneysurfer.sync.api.SyncSummary

internal class SyncSummaryBuilder {
    private var uploadedCount: Int = 0
    private var downloadedCount: Int = 0
    private var conflictCount: Int = 0
    private var recalculatedCount: Int = 0

    fun addUpload(count: Int) { uploadedCount += count }
    fun addDownload(count: Int) { downloadedCount += count }
    fun addConflicts(count: Int) { conflictCount += count }
    fun addRecalculated(count: Int) { recalculatedCount += count }

    fun build(): SyncSummary = SyncSummary(
        uploadedCount = uploadedCount,
        downloadedCount = downloadedCount,
        conflictCount = conflictCount,
        recalculatedCount = recalculatedCount,
    )
}
