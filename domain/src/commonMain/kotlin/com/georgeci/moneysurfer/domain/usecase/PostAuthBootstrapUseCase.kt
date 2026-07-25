package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import org.koin.core.annotation.Single

/**
 * After Firebase Auth completes we either find an existing `users/{uid}` document and pull the
 * user's workspaces into Room, or we create the doc as a first-time entry and let the workspace
 * selector handle workspace creation.
 *
 * For an existing user we resolve `defaultWorkspaceId` with a fallback to the first item in
 * `workspaceIds` — covers historical accounts where the default was never written. The resolved
 * id seeds `session.currentWorkspaceId` so a fresh device skips the selector and lands on
 * Dashboard.
 */
@Single
class PostAuthBootstrapUseCase(
    private val userRemoteRepository: UserRemoteRepository,
    private val workspaceSyncer: WorkspaceSyncer,
    private val sessionMutator: SessionMutator,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    sealed interface Result {
        data class ExistingUser(
            val workspaceIds: List<WorkspaceId>,
            val defaultWorkspaceId: WorkspaceId?,
        ) : Result

        data object FirstTime : Result
    }

    suspend operator fun invoke(
        uid: String,
        email: String?,
        displayName: String?,
        isAnon: Boolean,
    ): Either<AuthError, Result> = either {
        log.i { "[start] uid=${uid.redactUid()} isAnon=$isAnon hasEmail=${email != null}" }

        // Backfill the email→uid mapping so future invites can resolve targetUserId for this
        // account. Best-effort — failure shouldn't block the login flow, but it does block
        // anyone from inviting this user until the next successful bootstrap retries the write.
        when {
            !email.isNullOrBlank() -> {
                log.d { "[email-map] start uid=${uid.redactUid()} email=${email.redactEmail()}" }
                Either.catch { userRemoteRepository.upsertEmailMapping(email = email, uid = uid) }
                    .onLeft { log.w(it) { "[email-map] upsert failed uid=${uid.redactUid()} — non-fatal" } }
                    .onRight { log.i { "[email-map] ok uid=${uid.redactUid()}" } }
            }
            isAnon -> log.d { "[email-map:skip] uid=${uid.redactUid()} — anonymous account, no email" }
            else -> log.w {
                "[email-map:skip] uid=${uid.redactUid()} — non-anon account but email is blank; " +
                    "Firebase Auth should have provided an email claim, this user will " +
                    "NOT be invitable until the mapping is backfilled"
            }
        }

        log.d { "[fetch] users/${uid.redactUid()} …" }
        val existing = Either.catch { userRemoteRepository.fetch(uid) }
            .onLeft { log.e(it) { "[fetch] users/${uid.redactUid()} failed" } }
            .onRight { user ->
                if (user != null) {
                    log.i {
                        "[fetch] users/${uid.redactUid()} found: workspaces=${user.workspaceIds.size} " +
                            "default=${user.defaultWorkspaceId} " +
                            "isAnon=${user.isAnon} hasDisplayName=${user.displayName != null}"
                    }
                } else {
                    log.i { "[fetch] users/${uid.redactUid()} not found (first-time entry)" }
                }
            }
            .mapLeft { it.toAuthError() }
            .bind()

        if (existing != null) {
            log.i { "[pull] existing user, syncing all workspaces" }
            // syncAll fetches users/{uid}.workspaceIds from remote and runs cursor-based pull
            // for every workspace — including ones not yet local. Fail-loud: a PERMISSION_DENIED
            // here means a stale workspaceIds ref; aborting keeps the sign-in screen visible
            // rather than sending users to a broken state.
            Either.catch { workspaceSyncer.syncAll() }
                .onLeft { log.e(it) { "[pull] syncAll failed" } }
                .mapLeft { it.toAuthError() }
                .bind()

            // Resolve the active workspace: server's choice wins, otherwise pick the first
            // workspace in the list. Historical user docs have `defaultWorkspaceId = null`
            // because the field was never written before — without this fallback those users
            // re-pick a workspace on every fresh device.
            val resolvedDefault = existing.defaultWorkspaceId
                ?: existing.workspaceIds.firstOrNull()

            if (resolvedDefault != null) {
                sessionMutator.setCurrentWorkspace(resolvedDefault)
                log.i { "[seed] currentWorkspaceId=${resolvedDefault.value}" }
            }

            Result.ExistingUser(
                workspaceIds = existing.workspaceIds,
                defaultWorkspaceId = resolvedDefault,
            ).also {
                log.i {
                    "[done] ExistingUser uid=${uid.redactUid()} workspaces=${it.workspaceIds.size} " +
                        "default=${it.defaultWorkspaceId}"
                }
            }
        } else {
            log.i { "[create] first-time user, creating users/${uid.redactUid()} …" }
            Either
                .catch {
                    userRemoteRepository.create(
                        uid = uid,
                        displayName = displayName,
                        email = email,
                        isAnon = isAnon,
                        createdAt = getCurrentTime().toEpochMilliseconds(),
                    )
                }
                .onLeft { log.e(it) { "[create] users/${uid.redactUid()} failed" } }
                .onRight { log.i { "[create] users/${uid.redactUid()} ok" } }
                .mapLeft { it.toAuthError() }
                .bind()
            Result.FirstTime.also { log.i { "[done] FirstTime uid=${uid.redactUid()}" } }
        }
    }

    /**
     * Domain layer can't import Firebase types, so we sniff the exception class name
     * for "PERMISSION_DENIED" / `FirebaseFirestoreException`. Far from elegant — the
     * proper fix is a `:data` boundary that throws typed domain exceptions. Until then
     * this keeps the UI from silently swallowing rules misconfiguration.
     */
    private fun Throwable.toAuthError(): AuthError {
        val signature = (this::class.simpleName.orEmpty() + "|" + (message ?: "")).uppercase()
        val type = when {
            "PERMISSION_DENIED" in signature -> AuthError.Type.PermissionDenied
            "FIRESTORE" in signature && "PERMISSION" in signature -> AuthError.Type.PermissionDenied
            else -> AuthError.Type.Unknown
        }
        return AuthError(type = type, message = message, cause = this)
    }

    private companion object {
        const val TAG = "PostAuthBootstrap"
    }
}
