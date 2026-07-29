package com.georgeci.moneysurfer.data.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single

/** `appConfig/mobile` — the server-owned version gate, as it sits on the wire. */
@Serializable
data class AppConfigDoc(
    val minSupportedAppVersionCode: Int = 0,
    val latestAppVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val message: String? = null,
)

/**
 * The one read behind the app-version gate.
 *
 * A seam rather than a direct `firestore` call inside the repository, for the reason
 * [AccountDeletionRemoteSource] and [UserConfigRemoteSource][com.georgeci.moneysurfer.data.sync.UserConfigRemoteSource]
 * exist: what is worth testing is the decision around the read — how a missing document, an
 * unreachable server and each combination of version fields resolve — and gitlive's client cannot
 * be constructed off Android.
 */
fun interface AppConfigRemoteSource {

    /** The gate document, or null when it has never been published. Throws on a failed read. */
    suspend fun fetchMobile(): AppConfigDoc?
}

@Single(binds = [AppConfigRemoteSource::class])
class FirebaseAppConfigRemoteSource(
    private val firestore: FirebaseFirestore,
) : AppConfigRemoteSource {

    override suspend fun fetchMobile(): AppConfigDoc? {
        val snapshot = firestore.collection(APP_CONFIG_COLLECTION).document(MOBILE_DOCUMENT).get()
        return if (snapshot.exists) snapshot.data(AppConfigDoc.serializer()) else null
    }

    private companion object {
        const val APP_CONFIG_COLLECTION = "appConfig"
        const val MOBILE_DOCUMENT = "mobile"
    }
}
