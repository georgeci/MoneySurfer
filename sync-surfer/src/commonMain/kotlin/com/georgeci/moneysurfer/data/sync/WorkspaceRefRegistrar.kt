package com.georgeci.moneysurfer.data.sync

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Keeps `users/{uid}.workspaceIds` in step with the workspace documents this device pushes.
 *
 * `CreateWorkspaceUseCase` registers the ref at creation time, but only when sync is enabled —
 * otherwise it would write a ref to a `workspaces/{wid}` document the disabled syncer never
 * creates (issue #342). Workspaces created while sync was dark therefore sit in Room and in the
 * outbox with no remote ref, and nothing else ever adds one: `addWorkspaceRef` is called from
 * exactly two use cases, both at creation/join time.
 *
 * The moment the flag is flipped, the outbox drains and those documents land on Firestore — so
 * this runs on the push, where the ref belongs. `SyncScope.AllUserData` resolves workspaces from
 * the remote user document, so without it a workspace is invisible on every other device and
 * after any reinstall, permanently.
 *
 * Idempotent: `addWorkspaceRef` is an `arrayUnion`, so re-registering an already-listed workspace
 * is a no-op. Members are registered too, not just owners — a member does belong to the
 * workspace, and rules would have rejected the push otherwise.
 */
@Single
class WorkspaceRefRegistrar(
    private val userRemoteRepository: UserRemoteRepository,
    private val session: SessionPointers,
) {
    private val log = Logger.withTag(TAG)

    /**
     * Fail-loud on purpose: the caller is an outbox push, so a throw marks the mutation failed and
     * the next drain retries both the document and the ref. Swallowing here would leave exactly
     * the doc-without-ref state this class exists to prevent, with nothing to notice it.
     */
    suspend fun register(workspaceId: String) {
        val uid = session.currentFirebaseUid.first()
        if (uid == null) {
            log.d { "[ref] skipped wid=$workspaceId — no Firebase session" }
            return
        }
        userRemoteRepository.addWorkspaceRef(uid, WorkspaceId(workspaceId))
        log.d { "[ref] users/{uid}.workspaceIds now covers wid=$workspaceId" }
    }

    private companion object {
        const val TAG = "WorkspaceRefRegistrar"
    }
}
