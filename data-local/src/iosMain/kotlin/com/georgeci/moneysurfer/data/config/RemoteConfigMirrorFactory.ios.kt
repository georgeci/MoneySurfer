package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import com.georgeci.moneysurfer.domain.storage.iosAppStorageFilePath

/** Server-flag mirror for iOS — see the Android factory. */
fun createRemoteConfigMirror(): RemoteConfigMirror = RemoteConfigMirrorImpl(
    createReplaceOnCorruptionDataStore(
        producePath = { iosAppStorageFilePath(REMOTE_FLAGS_FILE_NAME, isDatabase = false) },
    ),
)
