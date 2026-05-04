package com.georgeci.moneysurfer.sync.api

enum class SyncReason {
    APP_START,
    FOREGROUND,
    MANUAL,
    SWIPE_REFRESH,
    LOCAL_CHANGE,
    BACKGROUND,
}
