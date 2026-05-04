package com.georgeci.moneysurfer.sync.plugin

import kotlinx.serialization.DeserializationStrategy

interface RemoteDocument {
    val id: String
    fun <T> decode(deserializer: DeserializationStrategy<T>): T
    fun getLong(field: String): Long?
}
