package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.DeserializationStrategy

class FirebaseRemoteDocument(val snap: DocumentSnapshot) : RemoteDocument {
    override val id: String get() = snap.id
    override fun <T> decode(deserializer: DeserializationStrategy<T>): T = snap.data(deserializer)
    override fun getLong(field: String): Long? = runCatching { snap.get<Long>(field) }.getOrNull()
}
