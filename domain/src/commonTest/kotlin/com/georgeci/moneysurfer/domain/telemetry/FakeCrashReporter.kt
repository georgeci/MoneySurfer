package com.georgeci.moneysurfer.domain.telemetry

class FakeCrashReporter : CrashReporter {

    val breadcrumbs = mutableListOf<String>()
    val recorded = mutableListOf<Throwable>()
    val userIds = mutableListOf<String?>()
    var isCollectionEnabled: Boolean? = null
        private set

    override fun log(message: String) {
        breadcrumbs += message
    }

    override fun record(error: Throwable) {
        recorded += error
    }

    override fun setUserId(userId: String?) {
        userIds += userId
    }

    override fun setKey(key: String, value: String) = Unit

    override fun setKey(key: String, value: Boolean) = Unit

    override fun setCollectionEnabled(enabled: Boolean) {
        isCollectionEnabled = enabled
    }
}
