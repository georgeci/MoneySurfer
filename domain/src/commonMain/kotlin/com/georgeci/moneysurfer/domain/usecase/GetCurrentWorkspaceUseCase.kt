package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * The workspace the session points at, or null while nothing names one — nobody signed in, no
 * workspace selected, or a workspace row the device has not pulled yet. The three are one answer
 * here on purpose: a caller can only ever render "we cannot name it", and telling them apart would
 * be inventing a distinction no screen acts on.
 *
 * Read off [WorkspaceRepository.getAll] rather than the suspend `getById`, for the reason
 * [GetSpentMonthUseCase] observes a base currency the same way: a rename, or the pull that first
 * lands the row, has to reach the screen without something re-subscribing, and a one-shot read
 * latches whatever the device happened to hold at collection time.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetCurrentWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
) {

    operator fun invoke(): Flow<Workspace?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(null)
            workspaceRepository.getAll().map { rows -> rows.firstOrNull { it.id == workspaceId } }
        }.distinctUntilChanged()
}
