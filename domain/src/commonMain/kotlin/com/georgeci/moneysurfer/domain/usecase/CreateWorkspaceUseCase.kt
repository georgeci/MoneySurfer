package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.config.SyncSettings
import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.time.Instant

/**
 * End-to-end create-workspace flow:
 *  1. Local: insert Workspace + WorkspaceMember(OWNER) + default Category rows.
 *  2. Remote: push the workspace to Firestore and append the wid to `users/{uid}.workspaceIds`
 *     so other devices see it. Skipped for the local "demo" session (no Firebase uid) and
 *     whenever [SyncSettings] says sync is off.
 *  3. Pin the new workspace as the active one so the next screen lands on Dashboard.
 */
@Single
@Suppress("LongParameterList")
class CreateWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceMemberRepository: WorkspaceMemberRepository,
    private val categoryRepository: CategoryRepository,
    private val userRemoteRepository: UserRemoteRepository,
    private val workspaceSyncer: WorkspaceSyncer,
    private val session: SessionPointers,
    private val sessionMutator: SessionMutator,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val syncSettings: SyncSettings,
) {
    private val log = Logger.withTag(TAG)

    data class Params(
        val name: String,
        val description: String,
        val baseCurrency: CurrencyCode,
    )

    suspend operator fun invoke(params: Params): Either<CreateWorkspaceError, WorkspaceId> = either {
        val ownerId = session.currentUserId.first()
            ?: raise(CreateWorkspaceError.NoCurrentUser)
        val newId = WorkspaceId.uuid()
        val now = getCurrentTime()
        log.i { "[start] uid=${ownerId.value.redactUid()} wid=${newId.value} name='${params.name}'" }

        Either
            .catch { writeLocal(newId, ownerId, now, params) }
            .onLeft { log.e(it) { "[local] failed wid=${newId.value}" } }
            .mapLeft { CreateWorkspaceError.LocalWriteFailed(it) }
            .bind()
        log.i { "[local] ok wid=${newId.value}" }

        // Remote push pipeline. If `syncWorkspace` fails the use case fails LOUD
        // (returns Left) — UI must surface the error so the user can retry.
        // Skipping `addWorkspaceRef` / `setDefaultWorkspace` / pin keeps the global
        // state consistent: `users/{uid}.workspaceIds` won't reference a workspace
        // whose `members/{uid}` row never landed (would otherwise lock subsequent
        // pulls with PERMISSION_DENIED via firestore.rules `isMember`).
        //
        // Demo session (no Firebase uid) → no remote contract; success is local-only.
        //
        // The setting has to be checked HERE and not only inside `WorkspaceSyncer`: with sync off
        // `pushAll()` returns normally, which is indistinguishable from a landed push, so the
        // two `UserRemoteRepository` calls below would still run and fill
        // `users/{uid}.workspaceIds` with ids whose `workspaces/{wid}` document was never
        // created — exactly the dangling refs the block above exists to prevent (issue #342).
        val syncEnabled = syncSettings.isEnabled.first()
        val firebaseUid = session.currentFirebaseUid.first()
        if (firebaseUid != null && syncEnabled) {
            Either.catch { workspaceSyncer.pushAll() }
                .onLeft { log.w(it) { "[remote] pushAll failed wid=${newId.value}" } }
                .onRight { log.i { "[remote] pushAll ok wid=${newId.value}" } }
                .mapLeft { CreateWorkspaceError.RemoteSyncFailed(it) }
                .bind()

            // sync landed → register the workspace under users/{uid}.workspaceIds + pin as default.
            // These two are best-effort: a transient failure here doesn't invalidate the workspace,
            // it just means cross-device discovery is delayed until the next sync cycle.
            Either.catch { userRemoteRepository.addWorkspaceRef(firebaseUid, newId) }
                .onLeft {
                    log.w(it) {
                        "[remote] addWorkspaceRef failed uid=${firebaseUid.redactUid()} wid=${newId.value}"
                    }
                }
                .onRight {
                    log.i { "[remote] addWorkspaceRef ok uid=${firebaseUid.redactUid()} wid=${newId.value}" }
                }
            Either.catch { userRemoteRepository.setDefaultWorkspace(firebaseUid, newId) }
                .onLeft {
                    log.w(it) {
                        "[remote] setDefaultWorkspace failed uid=${firebaseUid.redactUid()} wid=${newId.value}"
                    }
                }
                .onRight {
                    log.i { "[remote] setDefaultWorkspace ok uid=${firebaseUid.redactUid()} wid=${newId.value}" }
                }
        } else {
            log.i {
                "[remote] skipped (firebaseUid=${firebaseUid != null} " +
                    "syncEnabled=$syncEnabled) — local-only workspace"
            }
        }

        sessionMutator.setCurrentWorkspace(newId)
        log.i { "[done] wid=${newId.value} pinned as current workspace" }

        newId
    }

    private suspend fun writeLocal(
        newId: WorkspaceId,
        ownerId: UserId,
        now: Instant,
        params: Params,
    ) {
        workspaceRepository.insert(
            Workspace(
                id = newId,
                name = params.name.trim(),
                description = params.description.trim(),
                baseCurrency = params.baseCurrency,
                ownerId = ownerId,
                createdAt = now,
                archived = false,
            ),
        )
        workspaceMemberRepository.insert(
            WorkspaceMember(
                userId = ownerId,
                workspaceId = newId,
                role = WorkspaceRole.OWNER,
                addedByUserId = ownerId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        DEFAULT_CATEGORY_SEEDS.forEach { seed ->
            categoryRepository.insert(
                Category(
                    id = CategoryId.uuid(),
                    workspaceId = newId,
                    name = seed.name,
                    type = seed.type,
                    parentId = null,
                    createdAt = now,
                    systemKind = seed.systemKind,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "CreateWorkspace"
    }
}
