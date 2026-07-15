package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.days

/**
 * Sends a workspace invitation:
 * 1. Validate caller is OWNER of [Params.workspaceId].
 * 2. Normalize email + reject invalid / duplicate-active-member / pending-invite cases.
 * 3. Resolve recipient via `userEmails/{email}` mapping — refuse if no user has
 *    registered with that email (UI surfaces it as a field-level error).
 * 4. Build invite with `expiresAt = now + INVITE_TTL_MILLIS`, status PENDING, persist.
 */
@Single
class SendInviteUseCase(
    private val memberRepository: WorkspaceMemberRepository,
    private val inviteRepository: WorkspaceInviteRepository,
    private val userRemoteRepository: UserRemoteRepository,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    data class Params(
        val workspaceId: WorkspaceId,
        val email: String,
        val role: WorkspaceRole,
    )

    suspend operator fun invoke(params: Params): Either<InviteError, WorkspaceInviteId> = either {
        val callerId = session.currentUserId.flow.first()
            ?: raise(InviteError.NoCurrentUser)

        val normalizedEmail = params.email.trim().lowercase()
        ensure(normalizedEmail.isValidEmail()) { InviteError.InvalidEmail }

        val members = memberRepository.getByWorkspaceId(params.workspaceId).first()
        val callerMember = members.firstOrNull { it.userId == callerId }
        ensure(callerMember?.role == WorkspaceRole.OWNER) { InviteError.NotOwner }

        val activeMember = members.firstOrNull {
            it.status == WorkspaceMemberStatus.ACTIVE && it.email?.lowercase() == normalizedEmail
        }
        ensure(activeMember == null) { InviteError.AlreadyMember(normalizedEmail) }

        val existingPending = inviteRepository.getByWorkspaceId(params.workspaceId).first()
            .firstOrNull { it.status == InviteStatus.PENDING && it.email == normalizedEmail }
        ensure(existingPending == null) { InviteError.AlreadyInvited(normalizedEmail) }

        // Hard-resolve recipient via userEmails/{email} mapping. Mapping is written by
        // PostAuthBootstrap on every login, so any registered account is discoverable.
        // Miss → UserNotFound (UI shows a field-level error so the inviter can correct
        // the address). Throw → RemoteSyncFailed so the caller can retry.
        val targetUserId = Either.catch { userRemoteRepository.findByEmail(normalizedEmail) }
            .onLeft { log.e(it) { "[targetUserId:lookup] failed email=${normalizedEmail.redactEmail()}" } }
            .mapLeft { InviteError.RemoteSyncFailed(it) }
            .bind()
        if (targetUserId == null) {
            log.i { "[targetUserId] miss email=${normalizedEmail.redactEmail()} — no registered user" }
            raise(InviteError.UserNotFound(normalizedEmail))
        }
        log.i { "[targetUserId] email=${normalizedEmail.redactEmail()} resolved=${targetUserId.value.redactUid()}" }

        val now = getCurrentTime()
        val newId = WorkspaceInviteId.uuid()
        val invite = WorkspaceInvite(
            id = newId,
            workspaceId = params.workspaceId,
            email = normalizedEmail,
            targetUserId = targetUserId,
            role = params.role,
            status = InviteStatus.PENDING,
            invitedByUserId = callerId,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + INVITE_TTL,
            respondedAt = null,
        )

        Either.catch { inviteRepository.upsert(invite) }
            .onLeft {
                log.e(it) {
                    "[local] upsert failed wid=${params.workspaceId.value} " +
                        "email=${normalizedEmail.redactEmail()}"
                }
            }
            .mapLeft { InviteError.LocalWriteFailed(it) }
            .bind()

        // Best-effort: seed recipient's invitedWorkspaceIds so their next syncAll()
        // discovers the invite without a collectionGroup query.
        // Non-fatal: if this fails the invite still lands via the outbox; the
        // recipient can trigger a manual sync which will re-seed this field.
        val firebaseUid = session.currentFirebaseUid.flow.first()
        if (firebaseUid != null) {
            Either.catch {
                userRemoteRepository.addInvitedWorkspaceRef(targetUserId.value, params.workspaceId)
            }.onLeft {
                log.w(it) {
                    "[remote] addInvitedWorkspaceRef failed uid=${targetUserId.value.redactUid()} " +
                        "wid=${params.workspaceId.value} — non-fatal"
                }
            }
        }

        log.i {
            "[done] wid=${params.workspaceId.value} email=${normalizedEmail.redactEmail()} role=${params.role} " +
                "id=${newId.value} expiresAt=${invite.expiresAt}"
        }
        newId
    }

    private companion object {
        const val TAG = "SendInvite"

        /** 14 days. Matches md/members.md decision. */
        val INVITE_TTL = 14.days
    }
}

private fun String.isValidEmail(): Boolean {
    if (isBlank()) return false
    val at = indexOf('@')
    if (at <= 0 || at == length - 1) return false
    return indexOf('.', startIndex = at) > at + 1
}
