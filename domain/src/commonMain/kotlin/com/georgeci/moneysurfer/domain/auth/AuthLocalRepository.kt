package com.georgeci.moneysurfer.domain.auth

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import org.koin.core.annotation.Single

/**
 * Local mirror of an authenticated session: ensures a row in the local users table exists
 * (workspaces.ownerId FK requires it) and pins the active session pointer in DataStore. Upsert
 * keeps repeated sign-ins idempotent.
 *
 * For Firebase-backed flows the [uid] is the auth uid; for the local "demo" flow it is the
 * fixed `PREFILLED_DEFAULT_USER_ID`. Either way the local user id matches the session pointer.
 */
@Single
class AuthLocalRepository(
    private val userRepository: UserRepository,
    private val sessionMutator: SessionMutator,
) {
    suspend fun createLocalUser(
        uid: String,
        email: String?,
        displayName: String?,
        isAnon: Boolean,
    ) {
        val id = UserId(uid)
        userRepository.upsert(
            User(
                id = id,
                displayName = displayName,
                email = email,
                isAnon = isAnon,
            ),
        )
        sessionMutator.setCurrentUser(id)
    }
}
