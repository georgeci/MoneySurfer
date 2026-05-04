package com.georgeci.moneysurfer.data.repository

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.model.RemoteAppConfig
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single

@Single(binds = [AppConfigRepository::class])
class AppConfigRepositoryImpl(
    private val firestore: FirebaseFirestore,
) : AppConfigRepository {

    private val log = Logger.withTag("AppConfigRepository")

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(): RemoteAppConfig? = runCatching {
        val snap = firestore
            .collection("appConfig")
            .document("mobile")
            .get()
        if (!snap.exists) return null
        val dto = snap.data(AppConfigDoc.serializer())
        RemoteAppConfig(
            minSupportedAppVersionCode = dto.minSupportedAppVersionCode,
            latestAppVersionCode = dto.latestAppVersionCode,
            forceUpdate = dto.forceUpdate,
            message = dto.message,
        )
    }.onFailure { error ->
        log.w(error) { "fetch failed — gate falls back to Supported" }
    }.getOrNull()
}

@Serializable
private data class AppConfigDoc(
    val minSupportedAppVersionCode: Int = 0,
    val latestAppVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val message: String? = null,
)
