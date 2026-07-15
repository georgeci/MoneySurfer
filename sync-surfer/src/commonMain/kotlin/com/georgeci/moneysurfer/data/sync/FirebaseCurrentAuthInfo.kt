package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.annotation.Single

@Single(binds = [CurrentAuthInfo::class])
class FirebaseCurrentAuthInfo(private val auth: FirebaseAuth) : CurrentAuthInfo {
    override fun diagnostics(): String {
        val user = auth.currentUser
        return if (user == null) {
            "uid=<null> NOT_SIGNED_IN"
        } else {
            "uid=${user.uid.redactUid()} email=${user.email.redactEmail()} anon=${user.isAnonymous}"
        }
    }
}
