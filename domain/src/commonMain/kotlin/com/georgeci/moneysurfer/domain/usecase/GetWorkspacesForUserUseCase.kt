package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class GetWorkspacesForUserUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {

    operator fun invoke(userId: UserId): Flow<List<Workspace>> =
        workspaceRepository.getByUserId(userId)
}
