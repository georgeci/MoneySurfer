package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.logging.redactEmail
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import org.koin.core.annotation.Single

/**
 * After Firebase Auth completes we either find an existing `users/{uid}` document and pull the
 * user's workspaces into Room, or we create the doc as a first-time entry and let the workspace
 * selector handle workspace creation.
 *
 * For an existing user we resolve `defaultWorkspaceId` with a fallback to the first item in
 * `workspaceIds` — covers historical accounts where the default was never written — and pin it
 * as `session.currentWorkspaceId`, but **only after checking the workspace actually landed in
 * Room**. Pinning an id the pull never hydrated routes the next cold start to Dashboard on top
 * of an empty database, with the selector never shown again (issue #342). When the remote lists
 * workspaces and none of them hydrated, the caller gets [Result.CloudDataUnavailable] instead.
 */
@Single
@Suppress("LongParameterList")
class PostAuthBootstrapUseCase(
    private val userRemoteRepository: UserRemoteRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceSyncer: WorkspaceSyncer,
    private val sessionMutator: SessionMutator,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    sealed interface Result {
        /**
         * [defaultWorkspaceId] is the workspace that was pinned — always one that exists in Room,
         * so it can be `null` even when [workspaceIds] is not (the user is a member of workspaces
         * that have not been hydrated on this device).
         */
        data class ExistingUser(
            val workspaceIds: List<WorkspaceId>,
            val defaultWorkspaceId: WorkspaceId?,
        ) : Result

        /**
         * The remote user document lists workspaces but the pull hydrated none of them — sync is
         * off, the network died mid-pull, or every ref is stale. Auth itself succeeded, so the
         * session stays valid; the UI tells the user their cloud data is not here rather than
         * dropping them into an empty selector as if the account were brand new.
         */
        data class CloudDataUnavailable(
            val workspaceIds: List<WorkspaceId>,
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
            hydrateExistingUser(uid = uid, existing = existing)
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
     * Pulls every workspace the remote user document lists, then picks the one to pin: the
     * server's default if it hydrated, otherwise the first list entry that did.
     */
    private suspend fun Raise<AuthError>.hydrateExistingUser(uid: String, existing: User): Result {
        log.i { "[pull] existing user, syncing all workspaces" }
        // syncAll fetches users/{uid}.workspaceIds from remote and runs cursor-based pull
        // for every workspace — including ones not yet local. Fail-loud: a PERMISSION_DENIED
        // here means the whole account is unreachable (individual stale refs are tolerated
        // inside the pull); aborting keeps the sign-in screen visible rather than sending
        // users to a broken state.
        Either.catch { workspaceSyncer.syncAll() }
            .onLeft { log.e(it) { "[pull] syncAll failed" } }
            .mapLeft { it.toAuthError() }
            .bind()

        // Historical user docs have `defaultWorkspaceId = null` because the field was never
        // written before — without the fallback to the first entry those users re-pick a
        // workspace on every fresh device. Both candidates are then filtered through Room:
        // `defaultWorkspaceId` is not guaranteed to be a member of `workspaceIds`
        // (server-side skew), and neither is guaranteed to have been hydrated by the pull.
        val preferred = existing.defaultWorkspaceId ?: existing.workspaceIds.firstOrNull()
        val resolvedDefault = preferred?.takeIf { isHydrated(it) }
            ?: existing.workspaceIds.firstOrNull { isHydrated(it) }

        if (resolvedDefault == null) {
            if (existing.workspaceIds.isEmpty()) {
                log.i { "[done] ExistingUser uid=${uid.redactUid()} with no workspaces" }
                return Result.ExistingUser(workspaceIds = emptyList(), defaultWorkspaceId = null)
            }
            log.w {
                "[done] CloudDataUnavailable uid=${uid.redactUid()} — remote lists " +
                    "${existing.workspaceIds.size} workspace(s), none of them reached Room; " +
                    "leaving currentWorkspaceId unset"
            }
            return Result.CloudDataUnavailable(workspaceIds = existing.workspaceIds)
        }

        sessionMutator.setCurrentWorkspace(resolvedDefault)
        log.i {
            "[done] ExistingUser uid=${uid.redactUid()} workspaces=${existing.workspaceIds.size} " +
                "currentWorkspaceId=${resolvedDefault.value}"
        }
        return Result.ExistingUser(
            workspaceIds = existing.workspaceIds,
            defaultWorkspaceId = resolvedDefault,
        )
    }

    /**
     * True when [workspaceId] has a row in the local database, i.e. the pull actually brought
     * the workspace down. A lookup failure counts as "not hydrated": pinning on a Room error
     * is the outcome this guard exists to prevent.
     */
    private suspend fun isHydrated(workspaceId: WorkspaceId): Boolean =
        Either.catch { workspaceRepository.getById(workspaceId) != null }
            .onLeft { log.w(it) { "[verify] local lookup failed wid=${workspaceId.value}" } }
            .getOrElse { false }

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
