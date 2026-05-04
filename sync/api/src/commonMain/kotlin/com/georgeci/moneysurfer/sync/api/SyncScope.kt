package com.georgeci.moneysurfer.sync.api

enum class SyncScope {
    UploadOnly,
    ActiveWorkspace,
    AllUserData,
    ChangedSinceLastSync,
}

fun SyncReason.toScope(): SyncScope = when (this) {
    SyncReason.APP_START -> SyncScope.ActiveWorkspace
    SyncReason.FOREGROUND -> SyncScope.ActiveWorkspace
    SyncReason.SWIPE_REFRESH -> SyncScope.ActiveWorkspace
    SyncReason.MANUAL -> SyncScope.AllUserData
    SyncReason.LOCAL_CHANGE -> SyncScope.UploadOnly
    SyncReason.BACKGROUND -> SyncScope.ChangedSinceLastSync
}

fun mergeScope(first: SyncScope, second: SyncScope): SyncScope = when {
    first == SyncScope.AllUserData || second == SyncScope.AllUserData ->
        SyncScope.AllUserData
    first == SyncScope.ActiveWorkspace || second == SyncScope.ActiveWorkspace ->
        SyncScope.ActiveWorkspace
    first == SyncScope.ChangedSinceLastSync || second == SyncScope.ChangedSinceLastSync ->
        SyncScope.ChangedSinceLastSync
    else ->
        SyncScope.UploadOnly
}
