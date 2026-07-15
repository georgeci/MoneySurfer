package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Pins [workspaceId] as the active workspace.
 *
 *  1. Local: writes `session.currentWorkspaceId` (DataStore). This is what `Get*UseCase`s
 *     observe; the active screens repaint immediately.
 *  2. Remote (best-effort): pushes `users/{uid}.defaultWorkspaceId` so a fresh device after
 *     re-login lands on the same workspace without prompting the selector. Skipped for the
 *     local/demo session (no Firebase uid). Network failures are swallowed — the local
 *     pointer remains source of truth for the running session.
 */
@Single
class SelectWorkspaceUseCase(
    private val session: SessionPointers,
    private val userRemoteRepository: UserRemoteRepository,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(workspaceId: WorkspaceId) {
        session.currentWorkspaceId.set(workspaceId)

        val firebaseUid = session.currentFirebaseUid.flow.first()
        if (firebaseUid != null) {
            Either.catch { userRemoteRepository.setDefaultWorkspace(firebaseUid, workspaceId) }
                .onLeft {
                    log.w(it) {
                        "[remote] setDefaultWorkspace failed uid=${firebaseUid.redactUid()} wid=${workspaceId.value}"
                    }
                }
                .onRight {
                    log.i {
                        "[remote] setDefaultWorkspace ok uid=${firebaseUid.redactUid()} wid=${workspaceId.value}"
                    }
                }
        } else {
            log.i { "[remote] skipped (no Firebase uid — local/demo session)" }
        }
    }

    private companion object {
        const val TAG = "SelectWorkspace"
    }
}
