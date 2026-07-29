package com.georgeci.moneysurfer.data.repository

import io.kotest.core.spec.style.StringSpec

/**
 * Desktop has no Crashlytics, so the binding there is a reporter that does nothing. What this pins
 * down is that it does nothing *quietly*: every call site treats reporting as fire-and-forget, so
 * one that threw — on a null user id at sign-out, say — would take down the caller that was only
 * trying to record a breadcrumb.
 */
class NoOpCrashReporterJvmTest : StringSpec({

    "every reporting call is a no-op, including the nullable user id" {
        with(NoOpCrashReporter()) {
            log("startup")
            record(IllegalStateException("boom"))
            setUserId("u-1")
            setUserId(null)
            setKey("workspace", "ws-1")
            setKey("offline", true)
            setCollectionEnabled(true)
            setCollectionEnabled(false)
        }
    }
})
