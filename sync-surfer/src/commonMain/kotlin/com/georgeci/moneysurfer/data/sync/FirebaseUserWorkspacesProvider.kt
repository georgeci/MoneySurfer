package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.remote.UserDoc
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Fetches `users/{uid}.workspaceIds` from Firestore so [PullRemoteChangesUseCaseImpl] can
 * discover workspaces not yet present in the local database (e.g. added by an admin or
 * accepted via invite on another device).
 */
@Single(binds = [UserWorkspacesProvider::class])
class FirebaseUserWorkspacesProvider(
    private val firestore: FirebaseFirestore,
    private val session: SessionPointers,
) : UserWorkspacesProvider {

    private suspend fun userDoc(): UserDoc? {
        val uid = session.currentFirebaseUid.first() ?: return null
        val snap = firestore.collection("users").document(uid).get()
        if (!snap.exists) return null
        return snap.data(UserDoc.serializer())
    }

    override suspend fun workspaceIds(): List<String> =
        userDoc()?.workspaceIds ?: emptyList()

    override suspend fun invitedWorkspaceIds(): List<String> =
        userDoc()?.invitedWorkspaceIds ?: emptyList()
}
