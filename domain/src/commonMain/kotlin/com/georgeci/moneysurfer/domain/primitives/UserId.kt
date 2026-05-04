package com.georgeci.moneysurfer.domain.primitives

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
@Serializable
value class UserId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun uuid(): UserId = UserId(Uuid.random().toString())
    }
}
