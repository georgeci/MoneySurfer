package com.georgeci.moneysurfer.sync.api

data class SyncSummary(
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val conflictCount: Int = 0,
    val recalculatedCount: Int = 0,
)
