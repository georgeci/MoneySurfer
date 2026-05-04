package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics

class FirebaseCrashReporter : CrashReporter {

    private val crashlytics = Firebase.crashlytics

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun record(error: Throwable) {
        crashlytics.recordException(error)
    }

    override fun setUserId(userId: String?) {
        crashlytics.setUserId(userId.orEmpty())
    }

    override fun setKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    override fun setKey(key: String, value: Boolean) {
        crashlytics.setCustomKey(key, value)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }
}
