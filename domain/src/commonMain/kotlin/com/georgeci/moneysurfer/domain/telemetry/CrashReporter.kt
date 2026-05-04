package com.georgeci.moneysurfer.domain.telemetry

interface CrashReporter {
    fun log(message: String)
    fun record(error: Throwable)
    fun setUserId(userId: String?)
    fun setKey(key: String, value: String)
    fun setKey(key: String, value: Boolean)
    fun setCollectionEnabled(enabled: Boolean)
}
