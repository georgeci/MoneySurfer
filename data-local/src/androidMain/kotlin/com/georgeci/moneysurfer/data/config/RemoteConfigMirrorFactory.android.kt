package com.georgeci.moneysurfer.data.config

import android.content.Context
import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import kotlinx.coroutines.CoroutineScope

/**
 * Server-flag mirror for Android, on its own DataStore file.
 *
 * A [Context] parameter rather than an `expect`/`actual` pair, because that is what Android needs for
 * `filesDir` and the other two platforms need nothing — the same shape `createDebugConfigSource`
 * uses. Only the online host binds this; the offline build has no remote layer to mirror.
 */
fun createRemoteConfigMirror(context: Context, scope: CoroutineScope): RemoteConfigMirror = RemoteConfigMirrorImpl(
    createReplaceOnCorruptionDataStore(
        producePath = { context.filesDir.resolve(REMOTE_FLAGS_FILE_NAME).absolutePath },
    ),
    scope,
)
