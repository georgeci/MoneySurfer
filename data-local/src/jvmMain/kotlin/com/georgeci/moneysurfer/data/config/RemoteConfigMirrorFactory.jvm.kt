package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import com.georgeci.moneysurfer.domain.storage.appDataDir
import java.io.File

/** Server-flag mirror for the desktop JVM build — see the Android factory. */
fun createRemoteConfigMirror(): RemoteConfigMirror = RemoteConfigMirrorImpl(
    createReplaceOnCorruptionDataStore(
        producePath = { File(appDataDir(), REMOTE_FLAGS_FILE_NAME).absolutePath },
    ),
)
