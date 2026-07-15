package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
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
 *     so other devices see it. Skipped for the local "demo" session (no Firebase uid).
 *  3. Pin the new workspace as the active one so the next screen lands on Dashboard.
 */
@Single
class CreateWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceMemberRepository: WorkspaceMemberRepository,
    private val categoryRepository: CategoryRepository,
    private val userRemoteRepository: UserRemoteRepository,
    private val workspaceSyncer: WorkspaceSyncer,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    private val log = Logger.withTag(TAG)

    data class Params(
        val name: String,
        val description: String,
        val baseCurrency: CurrencyCode,
    )

    suspend operator fun invoke(params: Params): Either<CreateWorkspaceError, WorkspaceId> = either {
        val ownerId = session.currentUserId.flow.first()
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
        val firebaseUid = session.currentFirebaseUid.flow.first()
        if (firebaseUid != null) {
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
            log.i { "[remote] skipped (no Firebase uid — local/demo session)" }
        }

        session.currentWorkspaceId.set(newId)
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
