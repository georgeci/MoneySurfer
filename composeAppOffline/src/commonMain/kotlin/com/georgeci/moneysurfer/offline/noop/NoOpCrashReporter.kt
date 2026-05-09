package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.telemetry.CrashReporter

class NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun record(error: Throwable) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setKey(key: String, value: String) = Unit
    override fun setKey(key: String, value: Boolean) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}
