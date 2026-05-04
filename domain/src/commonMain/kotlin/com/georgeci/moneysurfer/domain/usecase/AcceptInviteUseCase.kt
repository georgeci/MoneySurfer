package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Accepts a pending invitation:
 * 1. Validate caller, invite (PENDING + not expired + targets caller via email or targetUserId).
 * 2. Insert local [WorkspaceMember] row, snapshotting profile.
 * 3. Mark invite ACCEPTED locally.
 * 4. (Firebase session only) push `users/{uid}.workspaceIds += wid` and trigger a
 *    targeted [WorkspaceSyncer.syncWorkspace] so the recipient's local DB hydrates with
 *    only the newly joined workspace immediately — without re-syncing all workspaces or
 *    waiting for the next bootstrap cycle.
 *
 * Steps 4 are best-effort and log-only on failure: the invite is already accepted, the
 * local member row is in place, the next sync cycle will pick up the rest.
 */
@Single
class AcceptInviteUseCase(
    private val inviteRepository: WorkspaceInviteRepository,
    private val memberRepository: WorkspaceMemberRepository,
    private val userRepository: UserRepository,
    private val userRemoteRepository: UserRemoteRepository,
    private val workspaceSyncer: WorkspaceSyncer,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    suspend operator fun invoke(id: WorkspaceInviteId): Either<InviteError, Unit> = either {
        log.i { "[start] id=${id.value}" }

        val callerId = session.currentUserId.flow.first()
            ?: raise(InviteError.NoCurrentUser).also { log.w { "[reject] no current user" } }
        val caller = userRepository.getById(callerId)
            ?: raise(InviteError.NoCurrentUser).also {
                log.w { "[reject] users.${callerId.value} not found locally" }
            }

        val invite = inviteRepository.getById(id)
            ?: raise(InviteError.InviteNotFound).also { log.w { "[reject] invite ${id.value} not found" } }
        log.d {
            "[loaded] id=${id.value} wid=${invite.workspaceId.value} email=${invite.email} " +
                "target=${invite.targetUserId?.value} status=${invite.status} " +
                "expiresAt=${invite.expiresAt}"
        }

        ensure(invite.status == InviteStatus.PENDING) {
            log.w { "[reject] not PENDING — actual=${invite.status}" }
            InviteError.InviteNotPending
        }
        val now = getCurrentTime()
        ensure(now <= invite.expiresAt) {
            log.w { "[reject] expired now=$now > expiresAt=${invite.expiresAt}" }
            InviteError.InviteExpired
        }

        val emailMatches = caller.email?.lowercase() == invite.email.lowercase()
        val uidMatches = invite.targetUserId == callerId
        ensure(emailMatches || uidMatches) {
            log.w {
                "[reject] caller doesn't match invite — emailMatch=$emailMatches " +
                    "uidMatch=$uidMatches callerEmail=${caller.email} inviteEmail=${invite.email}"
            }
            InviteError.InviteNotFound
        }
        log.d { "[validated] caller=${callerId.value} matchedBy=${if (uidMatches) "uid" else "email"}" }

        val member = WorkspaceMember(
            userId = callerId,
            workspaceId = invite.workspaceId,
            role = invite.role,
            status = WorkspaceMemberStatus.ACTIVE,
            displayName = caller.displayName.orEmpty(),
            email = caller.email,
            addedByUserId = invite.invitedByUserId,
            createdAt = now,
            updatedAt = now,
        )

        Either.catch { memberRepository.insert(member) }
            .onLeft { log.e(it) { "[local] member insert failed wid=${invite.workspaceId.value}" } }
            .onRight {
                log.i {
                    "[local] member inserted wid=${invite.workspaceId.value} " +
                        "role=${invite.role}"
                }
            }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        Either.catch { inviteRepository.markAccepted(id) }
            .onLeft { log.e(it) { "[local] invite markAccepted failed id=${id.value}" } }
            .onRight { log.i { "[local] invite markAccepted id=${id.value}" } }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        // Best-effort remote follow-ups: register the workspace under the user's doc and
        // trigger a local sync so workspace data lands immediately. Failures here don't fail
        // the accept — the member row + ACCEPTED invite are already pushed via the outbox,
        // and the next bootstrap will pick up whatever this skipped.
        val firebaseUid = session.currentFirebaseUid.flow.first()
        if (firebaseUid.isNullOrBlank()) {
            log.i { "[remote:skip] no firebase session — local-only accept" }
        } else {
            Either.catch { userRemoteRepository.addWorkspaceRef(firebaseUid, invite.workspaceId) }
                .onLeft {
                    log.w(it) {
                        "[remote] addWorkspaceRef failed uid=$firebaseUid " +
                            "wid=${invite.workspaceId.value} — non-fatal"
                    }
                }
                .onRight {
                    log.i {
                        "[remote] addWorkspaceRef ok uid=$firebaseUid wid=${invite.workspaceId.value}"
                    }
                }

            Either.catch {
                userRemoteRepository.removeInvitedWorkspaceRef(firebaseUid, invite.workspaceId)
            }.onLeft {
                log.w(it) {
                    "[remote] removeInvitedWorkspaceRef failed uid=$firebaseUid " +
                        "wid=${invite.workspaceId.value} — non-fatal"
                }
            }.onRight {
                log.i { "[remote] removeInvitedWorkspaceRef ok uid=$firebaseUid wid=${invite.workspaceId.value}" }
            }

            Either.catch { workspaceSyncer.syncWorkspace(invite.workspaceId) }
                .onLeft {
                    log.w(it) {
                        "[remote] syncWorkspace failed wid=${invite.workspaceId.value} — " +
                            "non-fatal, next bootstrap will retry"
                    }
                }
                .onRight {
                    log.i { "[remote] syncWorkspace ok wid=${invite.workspaceId.value}" }
                }
        }

        log.i {
            "[done] uid=${callerId.value} wid=${invite.workspaceId.value} role=${invite.role}"
        }
    }

    private companion object {
        const val TAG = "AcceptInvite"
    }
}
