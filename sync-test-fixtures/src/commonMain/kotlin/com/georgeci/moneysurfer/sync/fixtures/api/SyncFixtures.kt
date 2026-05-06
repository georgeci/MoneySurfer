package com.georgeci.moneysurfer.sync.fixtures.api

import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.UploadProgress
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncSummary

/** Stable test ids — readable in failure output. */
fun syncRequestId(value: String = "req-1"): SyncRequestId = SyncRequestId(value)

fun aSyncSummary(
    uploadedCount: Int = 0,
    downloadedCount: Int = 0,
    conflictCount: Int = 0,
    recalculatedCount: Int = 0,
): SyncSummary = SyncSummary(
    uploadedCount = uploadedCount,
    downloadedCount = downloadedCount,
    conflictCount = conflictCount,
    recalculatedCount = recalculatedCount,
)

fun anUploadSummary(uploadedCount: Int = 0): UploadSummary = UploadSummary(uploadedCount)

fun aPullSummary(
    downloadedCount: Int = 0,
    conflictCount: Int = 0,
): PullSummary = PullSummary(downloadedCount, conflictCount)

fun anUploadProgress(
    entityType: String = "TRANSACTION",
    current: Int = 1,
    total: Int = 1,
): UploadProgress = UploadProgress(entityType, current, total)

fun aPullProgress(
    collection: String = "transactions",
    current: Int = 1,
    total: Int = 1,
): PullProgress = PullProgress(collection, current, total)
