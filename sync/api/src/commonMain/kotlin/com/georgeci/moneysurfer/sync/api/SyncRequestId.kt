package com.georgeci.moneysurfer.sync.api

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class SyncRequestId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun uuid(): SyncRequestId = SyncRequestId(Uuid.random().toString())
    }
}
